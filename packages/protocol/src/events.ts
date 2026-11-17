import { z } from 'zod'
import { Id, Iso8601Utc } from './entities.js'
import { ContentBlockSchema, UsageSummarySchema } from './content.js'

/**
 * 实时事件。客户端只信 `seq` 的单调性，不信时间戳。
 * 断线后按 sinceSeq 补拉，出现空洞即停止应用并重新对齐。
 */
export const BaseEventSchema = z.object({
  eventId: Id,
  traceId: z.string().min(1),
  /** 全局单调递增，增量同步的唯一游标 */
  seq: z.number().int().nonnegative(),
  /** 保留给二阶段的 Agent 账本 */
  ledgerVersion: z.number().int().nonnegative().optional(),
  createdAt: Iso8601Utc,
  type: z.string().min(1),
  payload: z.unknown(),
})
export type BaseEvent = z.infer<typeof BaseEventSchema>

export const ChatEventType = z.enum([
  'conversation.message.accepted',
  'conversation.message.started',
  'conversation.message.delta',
  'conversation.reasoning.delta',
  'conversation.citation.created',
  'conversation.tool.started',
  'conversation.tool.finished',
  'conversation.title.updated',
  'conversation.message.completed',
  'conversation.message.failed',
  'conversation.message.cancelled',
])
export type ChatEventType = z.infer<typeof ChatEventType>

export const MessageAcceptedPayload = z.object({
  conversationId: Id,
  userMessageId: Id,
  assistantMessageId: Id,
})
export type MessageAcceptedPayload = z.infer<typeof MessageAcceptedPayload>

export const MessageStartedPayload = z.object({
  conversationId: Id,
  messageId: Id,
  provider: z.string(),
  model: z.string(),
})
export type MessageStartedPayload = z.infer<typeof MessageStartedPayload>

export const BlockDeltaPayload = z.object({
  conversationId: Id,
  messageId: Id,
  blockIndex: z.number().int().nonnegative(),
  delta: z.string(),
})
export type BlockDeltaPayload = z.infer<typeof BlockDeltaPayload>

export const BlockStartPayload = z.object({
  conversationId: Id,
  messageId: Id,
  blockIndex: z.number().int().nonnegative(),
  block: ContentBlockSchema,
})
export type BlockStartPayload = z.infer<typeof BlockStartPayload>

export const MessageCompletedPayload = z.object({
  conversationId: Id,
  messageId: Id,
  blocks: z.array(ContentBlockSchema),
  usage: UsageSummarySchema,
  canContinue: z.boolean(),
})
export type MessageCompletedPayload = z.infer<typeof MessageCompletedPayload>

export const MessageFailedPayload = z.object({
  conversationId: Id,
  messageId: Id,
  code: z.string(),
  message: z.string(),
  retryable: z.boolean(),
})
export type MessageFailedPayload = z.infer<typeof MessageFailedPayload>

export const TitleUpdatedPayload = z.object({
  conversationId: Id,
  title: z.string(),
  source: z.enum(['auto', 'user', 'imported']),
})
export type TitleUpdatedPayload = z.infer<typeof TitleUpdatedPayload>

/** 增量同步：请求与响应 */
export const SyncRequestSchema = z.object({
  sinceSeq: z.number().int().nonnegative().default(0),
  limit: z.number().int().min(1).max(500).default(200),
})
export type SyncRequest = z.infer<typeof SyncRequestSchema>

export const SyncResponseSchema = z.object({
  seq: z.number().int().nonnegative(),
  hasMore: z.boolean(),
  conversations: z.array(z.unknown()),
  messages: z.array(z.unknown()),
  titles: z.array(z.unknown()),
})
export type SyncResponse = z.infer<typeof SyncResponseSchema>
