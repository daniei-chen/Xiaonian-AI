import { z } from 'zod'
import { ContentBlockSchema, UsageSummarySchema } from './content.js'

/** UTC RFC3339，Z 结尾，可选小数秒。与 DeepSeek 导出档案的时间格式一致。 */
export const Iso8601Utc = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/, 'must be UTC RFC3339 ending in Z')
export type Iso8601Utc = z.infer<typeof Iso8601Utc>

export const Id = z.string().uuid()

const isValidIana = (tz: string): boolean => {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: tz })
    return true
  } catch {
    return false
  }
}

/** IANA 时区名，运行时校验，不接受固定偏移 */
export const IanaTimezone = z
  .string()
  .min(1)
  .refine(isValidIana, { message: 'invalid IANA timezone' })

// ---------------------------------------------------------------- 账号

export const UserSchema = z.object({
  id: Id,
  phoneOrEmail: z.string().min(1),
  locale: z.string().min(2).default('zh-CN'),
  timezone: IanaTimezone,
  plan: z.enum(['free', 'pro']).default('free'),
  createdAt: Iso8601Utc,
  updatedAt: Iso8601Utc,
})
export type User = z.infer<typeof UserSchema>

export const DeviceSessionSchema = z.object({
  id: Id,
  userId: Id,
  deviceName: z.string().min(1),
  platform: z.enum(['android', 'ios', 'web', 'desktop']),
  /** 设备公钥（Ed25519），用于轮换后校验 */
  publicKey: z.string().min(1),
  status: z.enum(['active', 'revoked']).default('active'),
  lastSeenAt: Iso8601Utc.nullable().default(null),
  createdAt: Iso8601Utc,
})
export type DeviceSession = z.infer<typeof DeviceSessionSchema>

// ---------------------------------------------------------------- 会话

export const ConversationSchema = z.object({
  id: Id,
  userId: Id,
  title: z.string().nullable().default(null),
  titleSource: z.enum(['auto', 'user', 'imported']).default('auto'),
  status: z.enum(['active', 'archived', 'deleted']).default('active'),
  sourceImportId: z.string().nullable().default(null),
  lastMessageAt: Iso8601Utc.nullable().default(null),
  createdAt: Iso8601Utc,
  updatedAt: Iso8601Utc,
})
export type Conversation = z.infer<typeof ConversationSchema>

export const MessageRole = z.enum(['system', 'user', 'assistant', 'tool'])
export type MessageRole = z.infer<typeof MessageRole>

export const MessageStatus = z.enum(['pending', 'streaming', 'completed', 'failed', 'cancelled'])
export type MessageStatus = z.infer<typeof MessageStatus>

export const MessageSchema = z.object({
  id: Id,
  conversationId: Id,
  /** 分支靠 parentId 还原；编辑后重新生成会产生新节点 */
  parentId: Id.nullable().default(null),
  role: MessageRole,
  blocks: z.array(ContentBlockSchema),
  provider: z.string().nullable().default(null),
  model: z.string().nullable().default(null),
  status: MessageStatus,
  usage: UsageSummarySchema.nullable().default(null),
  errorCode: z.string().nullable().default(null),
  /** 末尾 assistant 是否允许继续生成 */
  canContinue: z.boolean().default(false),
  /** 会话内单调递增，用于流式补序与去重 */
  seq: z.number().int().nonnegative(),
  createdAt: Iso8601Utc,
  updatedAt: Iso8601Utc,
})
export type Message = z.infer<typeof MessageSchema>

// ---------------------------------------------------------------- 文件

export const FileState = z.enum([
  'LOCAL_ONLY',
  'UPLOADING',
  'PROCESSING',
  'READY',
  'EXPIRED',
  'FAILED',
  'DELETING',
  'DELETED',
])
export type FileState = z.infer<typeof FileState>

export const FilePurpose = z.enum(['user_data', 'text_recognition', 'rich_layout_recognition'])
export type FilePurpose = z.infer<typeof FilePurpose>

export const ConversationFileSchema = z.object({
  id: Id,
  userId: Id,
  conversationId: Id.nullable().default(null),
  /** 上传到哪家：deepseek / 未来的其他 provider */
  provider: z.string().min(1),
  providerFileId: z.string().nullable().default(null),
  name: z.string().min(1),
  mime: z.string().min(1),
  size: z.number().int().nonnegative(),
  purpose: FilePurpose.nullable().default(null),
  state: FileState,
  /** 内容哈希，用于重复上传去重 */
  sha256: z.string().nullable().default(null),
  expiresAt: Iso8601Utc.nullable().default(null),
  createdAt: Iso8601Utc,
  updatedAt: Iso8601Utc,
})
export type ConversationFile = z.infer<typeof ConversationFileSchema>

// ---------------------------------------------------------------- 计量与审计

export const CapabilityKind = z.enum(['chat', 'search', 'fetch', 'ocr', 'asr', 'tts', 'files'])
export type CapabilityKind = z.infer<typeof CapabilityKind>

export const UsageEventSchema = z.object({
  id: Id,
  userId: Id,
  conversationId: Id.nullable().default(null),
  messageId: Id.nullable().default(null),
  capability: CapabilityKind,
  provider: z.string().min(1),
  model: z.string().nullable().default(null),
  usage: UsageSummarySchema,
  traceId: z.string().min(1),
  createdAt: Iso8601Utc,
})
export type UsageEvent = z.infer<typeof UsageEventSchema>

export const AuditEventSchema = z.object({
  id: Id,
  traceId: z.string().min(1),
  userId: Id.nullable().default(null),
  actor: z.enum(['user', 'system', 'agent']),
  action: z.string().min(1),
  target: z.string().nullable().default(null),
  riskLevel: z.enum(['read', 'write', 'irreversible']).default('read'),
  result: z.enum(['ok', 'failed', 'denied']),
  detail: z.record(z.string(), z.unknown()).optional(),
  createdAt: Iso8601Utc,
})
export type AuditEvent = z.infer<typeof AuditEventSchema>
