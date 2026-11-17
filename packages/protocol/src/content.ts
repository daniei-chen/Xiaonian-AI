import { z } from 'zod'

/**
 * canonical 内容块。所有 provider（OpenAI / Anthropic / 各工具 API）
 * 的差异都在适配层被压平到这个联合类型，业务代码只认它。
 */

/** 工具调用的语义类型，跨 provider 统一 */
export const ToolCallKindSchema = z.enum([
  'search',
  'deep_search',
  'read_link',
  'image_search',
  'deep_image_search',
  'ocr',
  'fetch',
])
export type ToolCallKind = z.infer<typeof ToolCallKindSchema>

export const CitationSchema = z.object({
  url: z.string().url(),
  title: z.string().nullable(),
  /** 引用处所在正文，用于定位角标 */
  citedText: z.string().optional(),
  /** 本地按出现顺序编的序号，不是服务端下发的 */
  index: z.number().int().nonnegative().optional(),
})
export type Citation = z.infer<typeof CitationSchema>

export const ContentBlockSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('text'),
    text: z.string(),
    citations: z.array(CitationSchema).optional(),
  }),
  z.object({
    type: z.literal('reasoning'),
    text: z.string(),
    /** Anthropic 协议的思考签名，必须原样回传 */
    signature: z.string().optional(),
    elapsedSecs: z.number().nonnegative().optional(),
    /** 供应商单独计量的思考 token */
    thinkingTokens: z.number().int().nonnegative().optional(),
  }),
  z.object({
    type: z.literal('tool_call'),
    id: z.string(),
    name: z.string(),
    kind: ToolCallKindSchema,
    input: z.unknown(),
  }),
  z.object({
    type: z.literal('tool_result'),
    callId: z.string(),
    ok: z.boolean(),
    kind: ToolCallKindSchema,
    output: z.unknown(),
  }),
  z.object({
    type: z.literal('file'),
    fileId: z.string(),
    provider: z.string(),
    name: z.string(),
    mime: z.string(),
    size: z.number().int().nonnegative(),
  }),
  z.object({
    type: z.literal('image'),
    fileId: z.string(),
    provider: z.string(),
    width: z.number().int().nonnegative().optional(),
    height: z.number().int().nonnegative().optional(),
  }),
  z.object({
    type: z.literal('error'),
    code: z.string(),
    message: z.string(),
  }),
])
export type ContentBlock = z.infer<typeof ContentBlockSchema>

/**
 * 归一化用量。spec §18 的 14 个字段全部覆盖，
 * 并补上实测才发现的 thinkingTokens / serviceTier。
 */
export const UsageSummarySchema = z.object({
  inputTokens: z.number().int().nonnegative().default(0),
  outputTokens: z.number().int().nonnegative().default(0),
  /** 思考 token，供应商单独计量 */
  thinkingTokens: z.number().int().nonnegative().default(0),
  cacheHitTokens: z.number().int().nonnegative().default(0),
  cacheMissTokens: z.number().int().nonnegative().default(0),
  cacheWriteTokens: z.number().int().nonnegative().default(0),

  searchCalls: z.number().int().nonnegative().default(0),
  fetchCalls: z.number().int().nonnegative().default(0),
  ocrPages: z.number().int().nonnegative().default(0),
  asrSeconds: z.number().nonnegative().default(0),
  ttsCharacters: z.number().int().nonnegative().default(0),

  /** 空闲 / 高峰，计价差异 2 倍 */
  serviceTier: z.string().optional(),
  estimatedCostCny: z.number().nonnegative().default(0),
  /** 原始 usage 留档，便于对账 */
  raw: z.unknown().optional(),
})
export type UsageSummary = z.infer<typeof UsageSummarySchema>

export const emptyUsage = (): UsageSummary => UsageSummarySchema.parse({})

export const addUsage = (a: UsageSummary, b: Partial<UsageSummary>): UsageSummary => ({
  ...a,
  inputTokens: a.inputTokens + (b.inputTokens ?? 0),
  outputTokens: a.outputTokens + (b.outputTokens ?? 0),
  thinkingTokens: a.thinkingTokens + (b.thinkingTokens ?? 0),
  cacheHitTokens: a.cacheHitTokens + (b.cacheHitTokens ?? 0),
  cacheMissTokens: a.cacheMissTokens + (b.cacheMissTokens ?? 0),
  cacheWriteTokens: a.cacheWriteTokens + (b.cacheWriteTokens ?? 0),
  searchCalls: a.searchCalls + (b.searchCalls ?? 0),
  fetchCalls: a.fetchCalls + (b.fetchCalls ?? 0),
  ocrPages: a.ocrPages + (b.ocrPages ?? 0),
  asrSeconds: a.asrSeconds + (b.asrSeconds ?? 0),
  ttsCharacters: a.ttsCharacters + (b.ttsCharacters ?? 0),
  estimatedCostCny: a.estimatedCostCny + (b.estimatedCostCny ?? 0),
})

/** 搜索结果归一化结构。各家返回差异大，原始体留在 raw 里备查。 */
export const SearchResultSchema = z.object({
  url: z.string().url(),
  title: z.string(),
  snippet: z.string(),
  publishedAt: z.string().nullable().default(null),
  siteName: z.string().nullable().default(null),
  siteIcon: z.string().nullable().optional(),
  raw: z.unknown().optional(),
})
export type SearchResult = z.infer<typeof SearchResultSchema>

export const FetchedPageSchema = z.object({
  url: z.string().url(),
  title: z.string().nullable(),
  publishedAt: z.string().nullable().default(null),
  markdown: z.string(),
})
export type FetchedPage = z.infer<typeof FetchedPageSchema>
