import type { PGlite } from '@electric-sql/pglite'
import { UpstreamError, type ChatMessage } from '@xiaonian/adapters'
import { apiError, emptyUsage, type Citation, type ContentBlock, type Message, type UsageSummary } from '@xiaonian/protocol'
import type { FastifyInstance } from 'fastify'
import type { ServerResponse } from 'node:http'
import type { CapabilitiesRuntime } from '../capabilities.js'
import { newId } from '../db/client.js'
import * as repo from '../db/repo.js'
import { abortStream, registerStream, unregisterStream } from '../stream/active.js'

/** P0 开发期固定用户；接真实登录后从令牌里取 */
export const DEV_USER_ID = '00000000-0000-4000-8000-000000000001'

export interface ConversationRoutesDeps {
  db: PGlite
  caps: CapabilitiesRuntime
}

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

type SendEvent = (event: string, data: unknown) => void

const openSse = (raw: ServerResponse): SendEvent => {
  raw.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  })
  raw.flushHeaders?.()
  return (event, data) => {
    raw.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`)
  }
}

/**
 * 回传给上游的历史：只带正文。
 * 思考块不回传（各 provider 语义不一致，且会显著抬高成本）。
 */
const toChatMessages = (messages: Message[]): ChatMessage[] =>
  messages
    .filter((m) => m.status === 'completed' || m.status === 'cancelled')
    .map((m) => ({
      role: (m.role === 'user' ? 'user' : 'assistant') as ChatMessage['role'],
      blocks: m.blocks.filter((b) => b.type === 'text'),
    }))
    .filter((m) => m.blocks.length > 0)

const appendDelta = (
  blocks: ContentBlock[],
  index: number,
  kind: 'text' | 'reasoning',
  delta: string,
): void => {
  const cur = blocks[index]
  if (cur && cur.type === kind) {
    blocks[index] = { ...cur, text: cur.text + delta }
  } else if (kind === 'text') {
    blocks[index] = { type: 'text', text: delta }
  } else {
    blocks[index] = { type: 'reasoning', text: delta }
  }
}

const errorCodeOf = (err: unknown): string =>
  err instanceof UpstreamError ? err.code : 'internal'

const errorMessageOf = (err: unknown): string =>
  err instanceof Error ? err.message : String(err)

export const registerConversationRoutes = (
  app: LooseFastify,
  { db, caps }: ConversationRoutesDeps,
): void => {
  // ------------------------------------------------------------ 会话

  app.get('/api/v1/conversations', async (req, reply) => {
    try {
      const items = await repo.listConversations(db, DEV_USER_ID)
      return { items }
    } catch (err) {
      req.log.error({ err }, 'list conversations failed')
      return reply.code(500).send(apiError('internal', 'failed to list conversations', String(req.id)))
    }
  })

  app.post<{ Body: { title?: string } }>('/api/v1/conversations', async (req, reply) => {
    const id = newId()
    const title = (req.body?.title ?? '').trim() || null
    const conversation = await repo.createConversation(db, DEV_USER_ID, id, title)
    return reply.code(201).send(conversation)
  })

  app.get<{ Params: { id: string } }>('/api/v1/conversations/:id', async (req, reply) => {
    const conversation = await repo.getConversation(db, DEV_USER_ID, req.params.id)
    if (!conversation) {
      return reply.code(404).send(apiError('not_found', 'conversation not found', String(req.id)))
    }
    const messages = await repo.listMessages(db, conversation.id)
    return { conversation, messages }
  })

  app.patch<{ Params: { id: string }; Body: { title?: string } }>(
    '/api/v1/conversations/:id',
    async (req, reply) => {
      const title = (req.body?.title ?? '').trim()
      if (!title) return reply.code(400).send(apiError('bad_request', 'title is required', String(req.id)))
      const conversation = await repo.getConversation(db, DEV_USER_ID, req.params.id)
      if (!conversation) {
        return reply.code(404).send(apiError('not_found', 'conversation not found', String(req.id)))
      }
      await repo.renameConversation(db, DEV_USER_ID, conversation.id, title)
      return { ok: true, title }
    },
  )

  app.get<{ Params: { id: string } }>(
    '/api/v1/conversations/:id/messages',
    async (req, reply) => {
      const conversation = await repo.getConversation(db, DEV_USER_ID, req.params.id)
      if (!conversation) {
        return reply.code(404).send(apiError('not_found', 'conversation not found', String(req.id)))
      }
      const items = await repo.listMessages(db, conversation.id)
      return { items }
    },
  )

  // ------------------------------------------------------------ 发消息（SSE）

  app.post<{
    Params: { id: string }
    Body: {
      text?: string
      thinking?: boolean
      effort?: 'low' | 'high' | 'max'
      maxTokens?: number
      /** 联网搜索：只有支持服务端工具的通道能接 */
      webSearch?: boolean
      /** 显式指定 provider，不传走默认 */
      provider?: string
    }
  }>('/api/v1/conversations/:id/messages', async (req, reply) => {
    const traceId = String(req.id)
    const conversation = await repo.getConversation(db, DEV_USER_ID, req.params.id)
    if (!conversation) {
      return reply.code(404).send(apiError('not_found', 'conversation not found', traceId))
    }

    const text = (req.body?.text ?? '').trim()
    if (!text) {
      return reply.code(400).send(apiError('bad_request', 'text is required', traceId))
    }

    const wantSearch = req.body?.webSearch === true
    let providerName = req.body?.provider ?? caps.defaultChatProvider

    // 联网要走服务端工具，只有 anthropic 通道支持；自动挑一个能接的
    if (wantSearch) {
      const capable = caps.chatProviders.find((p) =>
        caps.registry.chat(p).caps().features.includes('server-tools'),
      )
      if (capable) providerName = capable
    }

    if (!providerName) {
      return reply
        .code(503)
        .send(apiError('internal', 'no chat provider configured (check .env keys)', traceId))
    }

    const chat = caps.registry.chat(providerName)
    const configuredModel = caps.config.chat?.providers[providerName]?.model ?? null

    const userMessage = await repo.appendMessage(db, {
      id: newId(),
      conversationId: conversation.id,
      role: 'user',
      blocks: [{ type: 'text', text }],
      status: 'completed',
    })

    const assistantMessage = await repo.appendMessage(db, {
      id: newId(),
      conversationId: conversation.id,
      parentId: userMessage.id,
      role: 'assistant',
      blocks: [],
      status: 'streaming',
      provider: providerName,
      model: configuredModel,
    })

    // 首条消息顺手当标题（不调模型，省一次请求）
    if (!conversation.title) {
      await repo.setTitleIfEmpty(db, conversation.id, text.slice(0, 24))
    }

    const history = await repo.listMessages(db, conversation.id)
    const chatMessages = toChatMessages(history.filter((m) => m.id !== assistantMessage.id))

    reply.hijack()
    const raw = reply.raw
    const send = openSse(raw)

    const ac = new AbortController()
    registerStream(assistantMessage.id, DEV_USER_ID, ac)

    let clientGone = false
    raw.on('close', () => {
      clientGone = true
      ac.abort()
    })

    const blocks: ContentBlock[] = []
    const citations: Citation[] = []
    let usage: UsageSummary = emptyUsage()
    let finishReason: 'end_turn' | 'max_tokens' | 'tool_use' | 'refusal' = 'end_turn'
    let failure: { code: string; message: string } | null = null

    send('message.accepted', {
      conversationId: conversation.id,
      userMessageId: userMessage.id,
      assistantMessageId: assistantMessage.id,
    })

    try {
      for await (const ev of chat.stream(
        {
          messages: chatMessages,
          maxTokens: req.body?.maxTokens ?? 2048,
          thinking: { enabled: req.body?.thinking ?? false, effort: req.body?.effort ?? 'high' },
          ...(wantSearch ? { tools: ['deepseek_web_search'] } : {}),
        },
        { userId: DEV_USER_ID, traceId, signal: ac.signal, isolationKey: DEV_USER_ID },
      )) {
        switch (ev.type) {
          case 'start':
            send('message.started', { provider: ev.provider, model: ev.model })
            break
          case 'block_start':
            blocks[ev.index] = ev.block
            send('block.start', { index: ev.index, block: ev.block })
            break
          case 'text_delta':
            appendDelta(blocks, ev.index, 'text', ev.text)
            send('text.delta', { index: ev.index, text: ev.text })
            break
          case 'reasoning_delta':
            appendDelta(blocks, ev.index, 'reasoning', ev.text)
            send('reasoning.delta', { index: ev.index, text: ev.text })
            break
          case 'citations':
            citations.push(...ev.citations)
            send('citations', { items: ev.citations })
            break
          case 'usage':
            usage = ev.usage
            break
          case 'finish':
            finishReason = ev.finishReason
            break
        }
      }
    } catch (err) {
      req.log.warn({ err, messageId: assistantMessage.id }, 'stream ended with error')
      failure = { code: errorCodeOf(err), message: errorMessageOf(err) }
    }

    const finalBlocks = blocks.filter((b): b is ContentBlock => Boolean(b))

    // 引用挂在第一个正文块上，历史回看时还能拿到来源
    if (citations.length > 0) {
      const at = finalBlocks.findIndex((b) => b.type === 'text')
      if (at >= 0) {
        const target = finalBlocks[at]
        if (target && target.type === 'text') {
          finalBlocks[at] = { ...target, citations }
        }
      }
    }

    try {
      if (failure) {
        await repo.updateMessage(db, assistantMessage.id, {
          blocks: finalBlocks,
          status: clientGone ? 'cancelled' : 'failed',
          usage,
          errorCode: failure.code,
        })
        if (!clientGone) {
          send('message.failed', { messageId: assistantMessage.id, ...failure })
        }
      } else {
        const canContinue = finishReason === 'max_tokens'
        await repo.updateMessage(db, assistantMessage.id, {
          blocks: finalBlocks,
          status: 'completed',
          usage,
          canContinue,
        })
        if (!clientGone) {
          send('message.completed', {
            messageId: assistantMessage.id,
            blocks: finalBlocks,
            usage,
            canContinue,
          })
        }
      }
      await repo.touchConversation(db, conversation.id, new Date().toISOString())
    } catch (err) {
      req.log.error({ err }, 'persisting message failed')
    } finally {
      unregisterStream(assistantMessage.id)
      if (!clientGone) raw.end()
    }
  })

  // ------------------------------------------------------------ 停止

  app.post<{ Params: { id: string } }>('/api/v1/messages/:id/stop', async (req, reply) => {
    const ok = abortStream(req.params.id, DEV_USER_ID)
    if (!ok) {
      return reply.code(404).send(apiError('not_found', 'no active stream for this message', String(req.id)))
    }
    return { ok: true }
  })
}
