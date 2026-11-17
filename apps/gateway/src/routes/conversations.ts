import type { PGlite } from '@electric-sql/pglite'
import { UpstreamError, type ChatMessage } from '@xiaonian/adapters'
import { apiError, emptyUsage, type Citation, type ContentBlock, type Message, type UsageSummary } from '@xiaonian/protocol'
import type { FastifyInstance } from 'fastify'
import type { ServerResponse } from 'node:http'
import type { CapabilitiesRuntime } from '../capabilities.js'
import { newId } from '../db/client.js'
import * as repo from '../db/repo.js'
import { abortStream, registerStream, unregisterStream } from '../stream/active.js'
import { deepSearchSystemPrompt, runDeepSearch } from '../search/deep-search.js'

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
 * 回传给上游的历史：正文、带签名的思考块、文件与图片引用。
 * 没签名的思考块不能带——上游会校验签名，带上反而 400。
 */
const toChatMessages = (messages: Message[]): ChatMessage[] =>
  messages
    .filter((m) => m.status === 'completed' || m.status === 'cancelled')
    .map((m) => ({
      role: (m.role === 'user' ? 'user' : 'assistant') as ChatMessage['role'],
      blocks: m.blocks.filter(
        (b) =>
          b.type === 'text' ||
          b.type === 'file' ||
          b.type === 'image' ||
          (b.type === 'reasoning' && Boolean(b.signature)),
      ),
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
      /** 深度搜索：用能力层自己搜 + 抓正文 + 编号引用，不依赖上游工具 */
      deepSearch?: boolean
      /** 已上传的文件引用（file_id 来自 /api/v1/files） */
      files?: Array<{
        fileId: string
        kind?: 'image' | 'document'
        name?: string
        mime?: string
        size?: number
      }>
      /** 显式指定 provider，不传走默认 */
      provider?: string
      /** 从这个消息开始重放：先删掉它及其之后的所有消息，再重新生成 */
      replayFromMessageId?: string
      /** 继续生成：取最后一条助手回答，接着写 */
      continueGeneration?: boolean
    }
  }>('/api/v1/conversations/:id/messages', async (req, reply) => {
    const traceId = String(req.id)
    const conversation = await repo.getConversation(db, DEV_USER_ID, req.params.id)
    if (!conversation) {
      return reply.code(404).send(apiError('not_found', 'conversation not found', traceId))
    }

    // 重放：删掉目标消息及其之后的全部消息，等同于"从未发生过"
    const replayFrom = req.body?.replayFromMessageId
    if (replayFrom) {
      const target = await repo.getMessage(db, replayFrom)
      if (target && target.conversationId === conversation.id) {
        await db.query(
          `DELETE FROM messages WHERE conversation_id = $1 AND seq >= $2`,
          [conversation.id, target.seq],
        )
      }
    }

    const wantContinue = req.body?.continueGeneration === true
    let text = (req.body?.text ?? '').trim()
    /** 续写时锚定到目标回答；此时不新建用户消息，提示词也不落库 */
    let continueTarget: Message | null = null

    if (wantContinue) {
      const history = await repo.listMessages(db, conversation.id)
      const lastAssistant = [...history].reverse().find((m) => m.role === 'assistant')
      if (!lastAssistant) {
        return reply
          .code(400)
          .send(apiError('bad_request', 'nothing to continue from', traceId))
      }
      continueTarget = lastAssistant
      // 用提示词驱动续写，比依赖 assistant 预填充更稳（各兼容层支持不一致）
      text =
        '请直接接着你上一条回答继续输出剩余内容。不要重复已经说过的部分，' +
        '不要加任何开场白或说明。'
    }

    const fileRefs = req.body?.files ?? []
    // 允许只发附件不写文字
    if (!text && fileRefs.length === 0) {
      return reply.code(400).send(apiError('bad_request', 'text or files required', traceId))
    }

    const wantSearch = req.body?.webSearch === true
    const wantDeepSearch = req.body?.deepSearch === true
    // 联网与深度搜索都走自建的 search loop，区别只在于抓不抓正文。
    // 这样两条路都是"我们的实现"，不再把 tools 透传给上游。
    const useSearchLoop =
      (wantSearch || wantDeepSearch) && caps.defaults.search !== null && text.length > 0
    const deepMode = wantDeepSearch
    let providerName = req.body?.provider ?? caps.defaultChatProvider

    // 带文件时必须走 anthropic 通道（只有它支持 file_id 引用）
    if (fileRefs.length > 0) {
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

    const userBlocks: ContentBlock[] = fileRefs.map((f) =>
      f.kind === 'image'
        ? { type: 'image' as const, fileId: f.fileId, provider: 'deepseek' }
        : {
            type: 'file' as const,
            fileId: f.fileId,
            provider: 'deepseek',
            name: f.name ?? 'file',
            mime: f.mime ?? 'application/octet-stream',
            size: f.size ?? 0,
          },
    )
    if (text) userBlocks.push({ type: 'text', text })

    // 续写不产生用户消息：提示词只是驱动手段，不该出现在对话里
    const userMessage = continueTarget
      ? null
      : await repo.appendMessage(db, {
          id: newId(),
          conversationId: conversation.id,
          role: 'user',
          blocks: userBlocks,
          status: 'completed',
        })

    const parentForAssistant = userMessage?.id ?? continueTarget?.id

    const assistantMessage = await repo.appendMessage(db, {
      id: newId(),
      conversationId: conversation.id,
      ...(parentForAssistant ? { parentId: parentForAssistant } : {}),
      role: 'assistant',
      blocks: [],
      status: 'streaming',
      provider: providerName,
      model: configuredModel,
    })

    // 首条消息顺手当标题（不调模型，省一次请求）；续写不改标题
    if (!conversation.title && text && !continueTarget) {
      await repo.setTitleIfEmpty(db, conversation.id, text.slice(0, 24))
    }

    const history = await repo.listMessages(db, conversation.id)
    const chatMessages = toChatMessages(history.filter((m) => m.id !== assistantMessage.id))

    // 续写的提示词只发给上游，不进历史（落库的话会变成一条假的用户消息）
    if (continueTarget && text) {
      chatMessages.push({ role: 'user', blocks: [{ type: 'text', text }] })
    }

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
      userMessageId: userMessage?.id ?? null,
      assistantMessageId: assistantMessage.id,
    })

    // 检索：搜 → （深度模式才抓正文）→ 编号注入
    let systemPrompt: string | undefined
    const preCitations: Citation[] = []

    if (useSearchLoop && caps.defaults.search) {
      send('search.started', { query: text, deep: deepMode })
      try {
        const outcome = await runDeepSearch(
          caps.registry,
          text,
          { userId: DEV_USER_ID, traceId, isolationKey: DEV_USER_ID },
          req.log,
          {
            searchProvider: caps.defaults.search,
            fetchProvider: deepMode ? caps.defaults.fetch : null,
            maxFetch: deepMode ? 3 : 0,
          },
        )
        preCitations.push(...outcome.citations)
        systemPrompt = deepSearchSystemPrompt(
          outcome.context,
          new Date().toISOString().slice(0, 10),
        )
        // 先给前端来源，用户能立刻看到"搜到了什么"
        if (outcome.citations.length > 0) send('citations', { items: outcome.citations })
        send('search.done', { fetched: outcome.fetchCalls, total: outcome.resultCount })
      } catch (err) {
        req.log.warn({ err }, 'search loop failed')
        send('search.failed', { message: String(err) })
      }
    }

    try {
      for await (const ev of chat.stream(
        {
          messages: chatMessages,
          ...(systemPrompt ? { system: systemPrompt } : {}),
          maxTokens: req.body?.maxTokens ?? 2048,
          thinking: { enabled: req.body?.thinking ?? false, effort: req.body?.effort ?? 'high' },
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
          case 'signature': {
            // 签名挂回思考块，下一轮才能原样回传给上游
            const target = blocks[ev.index]
            if (target && target.type === 'reasoning') {
              blocks[ev.index] = { ...target, signature: ev.signature }
            }
            break
          }
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

    // 深度搜索的来源在上游没有 citations 时兜底，否则历史里会丢来源
    if (citations.length === 0 && preCitations.length > 0) citations.push(...preCitations)

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
