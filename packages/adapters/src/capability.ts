import type {
  CapabilityKind,
  Citation,
  ContentBlock,
  FetchedPage,
  SearchResult,
  UsageSummary,
} from '@xiaonian/protocol'

/** provider 自述支持什么、限制在哪 */
export interface ProviderCaps {
  features: readonly string[]
  limits?: Readonly<Record<string, number | string>>
}

export interface CallCtx {
  userId: string
  traceId: string
  signal?: AbortSignal
  /** 透传给上游做内容安全 / 缓存隔离 */
  isolationKey?: string
}

export interface CapabilityBase {
  /** 全局唯一："zhihui.chat" / "deepseek.search" */
  readonly id: string
  readonly provider: string
  readonly kind: CapabilityKind
  caps(): ProviderCaps
}

// ---------------------------------------------------------------- chat

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant' | 'tool'
  blocks: ContentBlock[]
}

export interface ChatInput {
  model?: string
  system?: string
  messages: ChatMessage[]
  maxTokens?: number
  temperature?: number
  thinking?: { enabled: boolean; effort?: 'low' | 'high' | 'max' }
  tools?: readonly string[]
}

export interface ChatOutput {
  blocks: ContentBlock[]
  usage: UsageSummary
  finishReason: 'end_turn' | 'max_tokens' | 'tool_use' | 'refusal'
  provider: string
  model: string
}

export type ChatStreamEvent =
  | { type: 'start'; provider: string; model: string }
  | { type: 'block_start'; index: number; block: ContentBlock }
  | { type: 'text_delta'; index: number; text: string }
  | { type: 'reasoning_delta'; index: number; text: string }
  /** 引用不是正文，单独一条事件；前端据此渲染来源卡片 */
  | { type: 'citations'; citations: Citation[] }
  | { type: 'usage'; usage: UsageSummary }
  | { type: 'finish'; finishReason: ChatOutput['finishReason'] }

export interface ChatCapability extends CapabilityBase {
  readonly kind: 'chat'
  readonly protocol: 'openai' | 'anthropic'
  complete(input: ChatInput, ctx: CallCtx): Promise<ChatOutput>
  stream(input: ChatInput, ctx: CallCtx): AsyncIterable<ChatStreamEvent>
}

// ---------------------------------------------------------------- 工具类

export interface SearchInput {
  query: string
  count?: number
  needContent?: boolean
  /** 触发搜索的用户原话，部分 provider 用它改善召回 */
  sourceQuestion?: string
}

export interface SearchCapability extends CapabilityBase {
  readonly kind: 'search'
  search(input: SearchInput, ctx: CallCtx): Promise<SearchResult[]>
}

export interface FetchCapability extends CapabilityBase {
  readonly kind: 'fetch'
  fetch(url: string, ctx: CallCtx): Promise<FetchedPage>
}

export interface OcrPage {
  index: number
  text: string
  markdown?: string
}

export interface OcrInput {
  data: Uint8Array
  filename: string
  mime: string
  purpose: 'text_recognition' | 'rich_layout_recognition'
}

export interface OcrCapability extends CapabilityBase {
  readonly kind: 'ocr'
  ocr(input: OcrInput, ctx: CallCtx): Promise<OcrPage[]>
}

export interface AsrInput {
  data: Uint8Array
  filename: string
  mime: string
}

export interface AsrResult {
  text: string
  durationMs: number
}

export interface AsrCapability extends CapabilityBase {
  readonly kind: 'asr'
  transcribe(input: AsrInput, ctx: CallCtx): Promise<AsrResult>
}

export interface TtsInput {
  text: string
  voice?: string
  format?: 'pcm' | 'wav'
}

export interface TtsCapability extends CapabilityBase {
  readonly kind: 'tts'
  /** 裸 PCM 帧流：24kHz / mono / 16-bit LE（provider 负责转成这个规格） */
  synthesize(input: TtsInput, ctx: CallCtx): AsyncIterable<Uint8Array>
}

export interface UploadedFile {
  provider: string
  providerFileId: string
  name: string
  mime: string
  size: number
}

export interface FilesCapability extends CapabilityBase {
  readonly kind: 'files'
  upload(input: { data: Uint8Array; filename: string; mime: string; purpose?: string }, ctx: CallCtx): Promise<UploadedFile>
  remove(providerFileId: string, ctx: CallCtx): Promise<void>
}

export type AnyCapability =
  | ChatCapability
  | SearchCapability
  | FetchCapability
  | OcrCapability
  | AsrCapability
  | TtsCapability
  | FilesCapability
