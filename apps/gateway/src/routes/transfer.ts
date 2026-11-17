import type { FastifyInstance } from 'fastify'
import type { PGlite } from '@electric-sql/pglite'
import { apiError, type ContentBlock } from '@xiaonian/protocol'
import { newId } from '../db/client.js'
import * as repo from '../db/repo.js'
import { unzip, zipStore } from '../lib/zip.js'

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

export interface TransferRoutesDeps {
  db: PGlite
  userId: string
}

interface Fragment {
  type?: string
  text?: string
  citations?: unknown
}

interface HistoryPayload {
  version?: number
  exportedAt?: string
  conversations?: Array<{
    title?: string | null
    createdAt?: string
    messages?: Array<{ role?: string; createdAt?: string; fragments?: Fragment[] }>
  }>
}

/**
 * 历史导入导出。
 *
 * 格式对齐 chat-demo 的 v3：每条消息是 fragments 数组而不是 blocks，
 * 这样和浏览器端导出的包可以互相读。
 */
export const registerTransferRoutes = (app: LooseFastify, { db, userId }: TransferRoutesDeps) => {
  app.get('/api/v1/export', async (req, reply) => {
    const conversations = await repo.listConversations(db, userId)

    const payload: HistoryPayload = {
      version: 3,
      exportedAt: new Date().toISOString(),
      conversations: [],
    }

    for (const conversation of conversations) {
      const messages = await repo.listMessages(db, conversation.id)
      payload.conversations!.push({
        title: conversation.title,
        createdAt: conversation.createdAt,
        messages: messages.map((m) => ({
          role: m.role,
          createdAt: m.createdAt,
          fragments: m.blocks.flatMap((b): Fragment[] => {
            if (b.type === 'text') {
              return [
                {
                  type: 'text',
                  text: b.text,
                  ...(b.citations && b.citations.length > 0 ? { citations: b.citations } : {}),
                },
              ]
            }
            if (b.type === 'reasoning') return [{ type: 'thinking', text: b.text }]
            return []
          }),
        })),
      })
    }

    const json = Buffer.from(JSON.stringify(payload, null, 2), 'utf8')
    const readme = Buffer.from(
      'Xiaonian 对话历史导出\n\nconversations.json 内含全部会话与消息（version 3）。\n',
      'utf8',
    )
    const zip = zipStore([
      { name: 'conversations.json', data: json },
      { name: 'README.txt', data: readme },
    ])

    req.log.info({ conversations: payload.conversations?.length ?? 0 }, 'history exported')
    return reply
      .header('Content-Type', 'application/zip')
      .header('Content-Disposition', 'attachment; filename="xiaonian-history.zip"')
      .send(zip)
  })

  app.post('/api/v1/import', { bodyLimit: 64 * 1024 * 1024 }, async (req, reply) => {
    const traceId = String(req.id)
    const body = req.body
    if (!Buffer.isBuffer(body) || body.length === 0) {
      return reply.code(400).send(apiError('bad_request', 'zip body required', traceId))
    }

    let entries: Map<string, Buffer>
    try {
      entries = unzip(body)
    } catch {
      return reply.code(400).send(apiError('bad_request', 'not a valid zip', traceId))
    }

    const raw = entries.get('conversations.json')
    if (!raw) {
      return reply
        .code(400)
        .send(apiError('bad_request', 'conversations.json missing in archive', traceId))
    }

    let payload: HistoryPayload
    try {
      payload = JSON.parse(raw.toString('utf8')) as HistoryPayload
    } catch {
      return reply.code(400).send(apiError('bad_request', 'conversations.json is not valid JSON', traceId))
    }

    let conversations = 0
    let messages = 0

    for (const item of payload.conversations ?? []) {
      const conversation = await repo.createConversation(
        db,
        userId,
        newId(),
        typeof item.title === 'string' ? item.title.slice(0, 120) : null,
      )
      conversations++

      for (const message of item.messages ?? []) {
        const blocks = (message.fragments ?? []).flatMap((f): ContentBlock[] => {
          if (f.type === 'text' && typeof f.text === 'string') {
            return [
              {
                type: 'text',
                text: f.text,
                ...(Array.isArray(f.citations) ? { citations: f.citations } : {}),
              } as ContentBlock,
            ]
          }
          if (f.type === 'thinking' && typeof f.text === 'string') {
            return [{ type: 'reasoning', text: f.text }]
          }
          return []
        })

        if (blocks.length === 0) continue

        await repo.appendMessage(db, {
          id: newId(),
          conversationId: conversation.id,
          role: message.role === 'assistant' ? 'assistant' : 'user',
          blocks,
          status: 'completed',
        })
        messages++
      }
    }

    req.log.info({ conversations, messages }, 'history imported')
    return { conversations, messages }
  })
}
