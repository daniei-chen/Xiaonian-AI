import type { PGlite } from '@electric-sql/pglite'
import type { ContentBlock, Conversation, Message, MessageRole, MessageStatus, UsageSummary } from '@xiaonian/protocol'

const iso = (v: unknown): string => {
  if (v instanceof Date) return v.toISOString()
  if (typeof v === 'string') return v
  return new Date(0).toISOString()
}

const isoOrNull = (v: unknown): string | null => (v == null ? null : iso(v))

interface ConversationRow {
  id: string
  user_id: string
  title: string | null
  title_source: string
  status: string
  source_import_id: string | null
  last_message_at: unknown
  created_at: unknown
  updated_at: unknown
}

interface MessageRow {
  id: string
  conversation_id: string
  parent_id: string | null
  seq: string | number
  role: string
  blocks: unknown
  provider: string | null
  model: string | null
  status: string
  usage: unknown
  error_code: string | null
  can_continue: boolean
  created_at: unknown
  updated_at: unknown
}

const mapConversation = (r: ConversationRow): Conversation => ({
  id: r.id,
  userId: r.user_id,
  title: r.title,
  titleSource: r.title_source as Conversation['titleSource'],
  status: r.status as Conversation['status'],
  sourceImportId: r.source_import_id,
  lastMessageAt: isoOrNull(r.last_message_at),
  createdAt: iso(r.created_at),
  updatedAt: iso(r.updated_at),
})

const mapMessage = (r: MessageRow): Message => ({
  id: r.id,
  conversationId: r.conversation_id,
  parentId: r.parent_id,
  role: r.role as MessageRole,
  blocks: (r.blocks ?? []) as ContentBlock[],
  provider: r.provider,
  model: r.model,
  status: r.status as MessageStatus,
  usage: (r.usage ?? null) as UsageSummary | null,
  errorCode: r.error_code,
  canContinue: r.can_continue,
  seq: Number(r.seq),
  createdAt: iso(r.created_at),
  updatedAt: iso(r.updated_at),
})

// ---------------------------------------------------------------- conversations

export const listConversations = async (db: PGlite, userId: string): Promise<Conversation[]> => {
  const { rows } = await db.query<ConversationRow>(
    `SELECT * FROM conversations
      WHERE user_id = $1 AND status <> 'deleted'
      ORDER BY COALESCE(last_message_at, updated_at) DESC
      LIMIT 100`,
    [userId],
  )
  return rows.map(mapConversation)
}

export const getConversation = async (
  db: PGlite,
  userId: string,
  conversationId: string,
): Promise<Conversation | null> => {
  const { rows } = await db.query<ConversationRow>(
    `SELECT * FROM conversations WHERE id = $1 AND user_id = $2 AND status <> 'deleted'`,
    [conversationId, userId],
  )
  const row = rows[0]
  return row ? mapConversation(row) : null
}

export const createConversation = async (
  db: PGlite,
  userId: string,
  id: string,
  title: string | null,
): Promise<Conversation> => {
  const { rows } = await db.query<ConversationRow>(
    `INSERT INTO conversations (id, user_id, title, title_source)
     VALUES ($1, $2, $3, 'auto')
     RETURNING *`,
    [id, userId, title],
  )
  const row = rows[0]
  if (!row) throw new Error('conversation insert returned no row')
  return mapConversation(row)
}

export const renameConversation = async (
  db: PGlite,
  userId: string,
  conversationId: string,
  title: string,
): Promise<void> => {
  await db.query(
    `UPDATE conversations SET title = $3, title_source = 'user', updated_at = now()
      WHERE id = $1 AND user_id = $2`,
    [conversationId, userId, title],
  )
}

export const touchConversation = async (
  db: PGlite,
  conversationId: string,
  at: string,
): Promise<void> => {
  await db.query(
    `UPDATE conversations SET last_message_at = $2, updated_at = now() WHERE id = $1`,
    [conversationId, at],
  )
}

/** 只在还没有标题时写入，不会覆盖用户手动改过的标题 */
export const setTitleIfEmpty = async (
  db: PGlite,
  conversationId: string,
  title: string,
): Promise<void> => {
  await db.query(
    `UPDATE conversations SET title = $2, updated_at = now() WHERE id = $1 AND title IS NULL`,
    [conversationId, title],
  )
}

// ---------------------------------------------------------------- messages

export const listMessages = async (
  db: PGlite,
  conversationId: string,
  limit = 200,
): Promise<Message[]> => {
  const { rows } = await db.query<MessageRow>(
    `SELECT * FROM messages WHERE conversation_id = $1 ORDER BY seq ASC LIMIT $2`,
    [conversationId, limit],
  )
  return rows.map(mapMessage)
}

export const getMessage = async (
  db: PGlite,
  messageId: string,
): Promise<Message | null> => {
  const { rows } = await db.query<MessageRow>(`SELECT * FROM messages WHERE id = $1`, [messageId])
  const row = rows[0]
  return row ? mapMessage(row) : null
}

export interface AppendMessageInput {
  id: string
  conversationId: string
  parentId?: string | null
  role: MessageRole
  blocks: ContentBlock[]
  status: MessageStatus
  provider?: string | null
  model?: string | null
  usage?: UsageSummary | null
  canContinue?: boolean
}

/**
 * seq 在会话内单调。PGlite 单进程 + messages(conversation_id, seq) 唯一约束，
 * 并发只会撞唯一键而不会产生空洞，重试即可。
 */
export const appendMessage = async (db: PGlite, input: AppendMessageInput): Promise<Message> => {
  const { rows } = await db.query<{ seq: string | number }>(
    `SELECT COALESCE(MAX(seq), 0) + 1 AS seq FROM messages WHERE conversation_id = $1`,
    [input.conversationId],
  )
  const seq = Number(rows[0]?.seq ?? 1)

  const { rows: inserted } = await db.query<MessageRow>(
    `INSERT INTO messages
       (id, conversation_id, parent_id, seq, role, blocks, provider, model, status, usage, can_continue)
     VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $8, $9, $10::jsonb, $11)
     RETURNING *`,
    [
      input.id,
      input.conversationId,
      input.parentId ?? null,
      seq,
      input.role,
      JSON.stringify(input.blocks),
      input.provider ?? null,
      input.model ?? null,
      input.status,
      input.usage ? JSON.stringify(input.usage) : null,
      input.canContinue ?? false,
    ],
  )
  const row = inserted[0]
  if (!row) throw new Error('message insert returned no row')
  return mapMessage(row)
}

export interface UpdateMessageInput {
  blocks?: ContentBlock[]
  status?: MessageStatus
  usage?: UsageSummary | null
  canContinue?: boolean
  errorCode?: string | null
}

export const updateMessage = async (
  db: PGlite,
  messageId: string,
  patch: UpdateMessageInput,
): Promise<void> => {
  await db.query(
    `UPDATE messages SET
       blocks       = COALESCE($2::jsonb, blocks),
       status       = COALESCE($3, status),
       usage        = COALESCE($4::jsonb, usage),
       can_continue = COALESCE($5, can_continue),
       error_code   = COALESCE($6, error_code),
       updated_at   = now()
     WHERE id = $1`,
    [
      messageId,
      patch.blocks ? JSON.stringify(patch.blocks) : null,
      patch.status ?? null,
      patch.usage ? JSON.stringify(patch.usage) : null,
      patch.canContinue ?? null,
      patch.errorCode ?? null,
    ],
  )
}
