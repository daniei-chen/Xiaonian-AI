import type { FastifyInstance } from 'fastify'
import type { PGlite } from '@electric-sql/pglite'
import { apiError, type Citation, type ContentBlock, type Message } from '@xiaonian/protocol'
import { newId } from '../db/client.js'
import * as repo from '../db/repo.js'
import { unzip, zipStore } from '../lib/zip.js'

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

export interface TransferRoutesDeps {
  db: PGlite
  userId: string
}

// ---------------------------------------------------------------- v3 归档类型

const FORMAT_VERSION = 3

type Iso = string

interface ArchiveSearchResult {
  url: string
  title: string
  snippet: string
  published_at: Iso | null
  site_name: string | null
}

interface ArchiveFileEntry {
  id: string
  file_name: string
  file_size: number
  token_usage: number | null
  inserted_at: Iso
  export_path: string | null
  is_image: boolean
  width: number | null
  height: number | null
}

type ArchiveFragment =
  | { type: 'REQUEST'; content: string }
  | { type: 'RESPONSE'; content: string }
  | { type: 'THINK'; content: string; elapsed_secs: number | null }
  | { type: 'TEMPLATE_RESPONSE'; content: string }
  | { type: 'TIP'; content: string; style: 'INFO' | 'WARNING' | 'CAUTION' }
  | { type: 'CONTENT_RISK'; content: string; style: 'INFO' | 'WARNING' | 'CAUTION' }
  | { type: 'PARTIAL_FILE_READ'; ratio: number }
  | {
      type: 'TOOL_SEARCH'
      queries: string[]
      results: ArchiveSearchResult[]
      status?: 'WIP' | 'FINISHED' | 'FAILED'
    }
  | {
      type: 'TOOL_OPEN'
      status: 'WIP' | 'FINISHED' | 'FAILED'
      url: string | null
      title: string | null
      snippet?: string | null
      site_name?: string | null
      published_at: Iso | null
    }
  | { type: 'TOOL_FIND'; status: 'WIP' | 'FINISHED' | 'NO_RESULT' | 'FAILED'; pattern?: string }
  | { type: 'READ_LINK'; results: Array<{ url?: string; title?: string | null; signature: string }> }
  | { type: 'FILE'; files: ArchiveFileEntry[] }
  | {
      type: 'TOOL_IMAGE_SEARCH'
      status: 'WIP' | 'FINISHED' | 'FAILED'
      results: Array<{
        title: string
        source_page_url: string
        snippet: string
        site_name: string | null
        published_at: Iso | null
      }>
      image_indexes?: number[]
    }

interface ArchiveMessage {
  id: number
  parent_id: number | null
  role: 'USER' | 'ASSISTANT'
  status: 'FINISHED'
  thinking_enabled: boolean
  search_enabled: boolean
  accumulated_token_usage: number
  inserted_at: Iso
  fragments: ArchiveFragment[]
}

interface ArchiveSession {
  id: string
  title: string | null
  pinned: boolean
  current_message_id: number
  inserted_at: Iso
  updated_at: Iso
  messages: ArchiveMessage[]
}

interface SessionIndexEntry {
  id: string
  title: string | null
  pinned: boolean
  message_count: number
  inserted_at: Iso
  updated_at: Iso
}

// ---------------------------------------------------------------- 导出：本地 -> 归档

const stripFilePrefix = (fileId: string): string => fileId.replace(/^file-/, '')

const toFileEntry = (
  block: Extract<ContentBlock, { type: 'file' | 'image' }>,
  insertedAt: Iso,
): ArchiveFileEntry => ({
  id: stripFilePrefix(block.fileId),
  file_name: block.type === 'file' ? block.name : 'image',
  file_size: block.type === 'file' ? block.size : 0,
  token_usage: null,
  inserted_at: insertedAt,
  export_path: null,
  is_image: block.type === 'image',
  width: null,
  height: null,
})

const toFragments = (message: Message): ArchiveFragment[] => {
  const out: ArchiveFragment[] = []
  const text = message.blocks
    .filter((b): b is Extract<ContentBlock, { type: 'text' }> => b.type === 'text')
    .map((b) => b.text)
    .join('\n')

  const attachments = message.blocks.filter(
    (b): b is Extract<ContentBlock, { type: 'file' | 'image' }> =>
      b.type === 'file' || b.type === 'image',
  )

  if (message.role === 'user') {
    if (attachments.length > 0) {
      out.push({
        type: 'FILE',
        files: attachments.map((b) => toFileEntry(b, message.createdAt)),
      })
    }
    if (text) out.push({ type: 'REQUEST', content: text })
    return out
  }

  // 助手：思考 -> 检索 -> 正文，顺序与来源一致
  const reasoning = message.blocks.find(
    (b): b is Extract<ContentBlock, { type: 'reasoning' }> => b.type === 'reasoning',
  )
  if (reasoning && reasoning.text) {
    out.push({
      type: 'THINK',
      content: reasoning.text,
      elapsed_secs: reasoning.elapsedSecs ?? null,
    })
  }

  const citations = message.blocks.find(
    (b): b is Extract<ContentBlock, { type: 'text' }> => b.type === 'text' && Boolean(b.citations?.length),
  )?.citations

  if (citations && citations.length > 0) {
    out.push({
      type: 'TOOL_SEARCH',
      queries: [],
      results: citations.map((c) => ({
        url: c.url,
        title: c.title ?? c.url,
        snippet: c.citedText ?? '',
        published_at: null,
        site_name: null,
      })),
      status: 'FINISHED',
    })
  }

  if (text) out.push({ type: 'RESPONSE', content: text })
  return out
}

const buildSession = (conversation: {
  id: string
  title: string | null
  createdAt: Iso
}, messages: Message[]): ArchiveSession => {
  const usable = messages
    .map((m) => ({ message: m, fragments: toFragments(m) }))
    .filter((item) => item.fragments.length > 0)

  let parent: number | null = null
  const archiveMessages: ArchiveMessage[] = usable.map((item, index) => {
    const id = index + 1
    const node: ArchiveMessage = {
      id,
      parent_id: parent,
      role: item.message.role === 'user' ? 'USER' : 'ASSISTANT',
      status: 'FINISHED',
      thinking_enabled: item.message.blocks.some((b) => b.type === 'reasoning'),
      search_enabled: item.message.blocks.some(
        (b) => b.type === 'text' && Boolean(b.citations?.length),
      ),
      accumulated_token_usage: 0,
      inserted_at: item.message.createdAt,
      fragments: item.fragments,
    }
    parent = id
    return node
  })

  return {
    id: conversation.id,
    title: conversation.title,
    pinned: false,
    current_message_id: archiveMessages.length,
    inserted_at: conversation.createdAt,
    updated_at: conversation.createdAt,
    messages: archiveMessages,
  }
}

// ---------------------------------------------------------------- 导入：归档 -> 本地

/** 一个归档消息的 fragments 压成 canonical blocks */
const fragmentsToBlocks = (fragments: ArchiveFragment[]): ContentBlock[] => {
  const blocks: ContentBlock[] = []
  const citations: Citation[] = []

  for (const fragment of fragments) {
    switch (fragment.type) {
      case 'REQUEST':
      case 'RESPONSE':
      case 'TEMPLATE_RESPONSE':
        if (fragment.content) blocks.push({ type: 'text', text: fragment.content })
        break
      case 'THINK':
        if (fragment.content) blocks.push({ type: 'reasoning', text: fragment.content })
        break
      case 'TOOL_SEARCH':
        fragment.results.forEach((r, i) => {
          if (citations.some((c) => c.url === r.url)) return
          citations.push({
            index: citations.length + 1 || i + 1,
            url: r.url,
            title: r.title,
            ...(r.snippet ? { citedText: r.snippet } : {}),
          })
        })
        break
      case 'TIP':
      case 'CONTENT_RISK':
      case 'PARTIAL_FILE_READ':
      case 'TOOL_OPEN':
      case 'TOOL_FIND':
      case 'READ_LINK':
      case 'FILE':
      case 'TOOL_IMAGE_SEARCH':
        // 这些在本端没有对应展示位置，丢弃而不是伪装成正文
        break
    }
  }

  if (citations.length > 0) {
    const target = blocks.find((b): b is Extract<ContentBlock, { type: 'text' }> => b.type === 'text')
    if (target) target.citations = citations
  }

  return blocks
}

export const registerTransferRoutes = (app: LooseFastify, { db, userId }: TransferRoutesDeps) => {
  app.get('/api/v1/export', async (req, reply) => {
    const conversations = await repo.listConversations(db, userId)

    const sessions: ArchiveSession[] = []
    for (const conversation of conversations) {
      const messages = await repo.listMessages(db, conversation.id)
      sessions.push(
        buildSession(
          { id: conversation.id, title: conversation.title, createdAt: conversation.createdAt },
          messages,
        ),
      )
    }

    const index: SessionIndexEntry[] = sessions.map((s) => ({
      id: s.id,
      title: s.title,
      pinned: s.pinned,
      message_count: s.messages.length,
      inserted_at: s.inserted_at,
      updated_at: s.updated_at,
    }))

    // 条目顺序按规范：manifest 第一，索引在全部会话之后
    const entries = [
      {
        name: 'manifest.json',
        data: Buffer.from(JSON.stringify({ export_format_version: FORMAT_VERSION }, null, 2)),
      },
      { name: 'README.txt', data: Buffer.from('Xiaonian 导出的对话历史（v3 兼容格式）\n') },
      ...sessions.map((s) => ({
        name: `chat_sessions/${s.id}.json`,
        data: Buffer.from(JSON.stringify(s, null, 2)),
      })),
      {
        name: 'chat_session_index.json',
        data: Buffer.from(JSON.stringify(index, null, 2)),
      },
    ]

    const zip = zipStore(entries)
    req.log.info({ sessions: sessions.length }, 'history exported')

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

    const manifestRaw = entries.get('manifest.json')
    if (manifestRaw) {
      try {
        const manifest = JSON.parse(manifestRaw.toString('utf8')) as { export_format_version?: number }
        if (typeof manifest.export_format_version === 'number' && manifest.export_format_version > FORMAT_VERSION) {
          return reply
            .code(400)
            .send(
              apiError(
                'bad_request',
                `archive version ${manifest.export_format_version} is newer than supported ${FORMAT_VERSION}`,
                traceId,
              ),
            )
        }
      } catch {
        // manifest 坏了不致命，继续按文件名找会话
      }
    }

    // 只认 chat_sessions/*.json；其它条目（图片、索引）本来就不需要
    const sessionEntries = [...entries.entries()].filter(
      ([name]) => name.startsWith('chat_sessions/') && name.endsWith('.json'),
    )

    if (sessionEntries.length === 0) {
      return reply
        .code(400)
        .send(apiError('bad_request', 'no chat_sessions/*.json in archive', traceId))
    }

    let conversations = 0
    let messages = 0

    for (const [, raw] of sessionEntries) {
      let session: ArchiveSession
      try {
        session = JSON.parse(raw.toString('utf8')) as ArchiveSession
      } catch {
        continue
      }
      if (!Array.isArray(session.messages)) continue

      const conversation = await repo.createConversation(
        db,
        userId,
        newId(),
        typeof session.title === 'string' ? session.title.slice(0, 120) : null,
      )
      conversations++

      // 消息树按 id 排序后线性化：本端没有分支概念，取主链
      const ordered = [...session.messages].sort((a, b) => a.id - b.id)
      for (const node of ordered) {
        const blocks = fragmentsToBlocks(node.fragments ?? [])
        if (blocks.length === 0) continue

        await repo.appendMessage(db, {
          id: newId(),
          conversationId: conversation.id,
          role: node.role === 'ASSISTANT' ? 'assistant' : 'user',
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
