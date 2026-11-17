import {
  emptyUsage,
  mapUpstreamStatus,
  type ContentBlock,
  type ErrorCode,
  type UsageSummary,
} from '@xiaonian/protocol'
import type {
  CallCtx,
  ChatCapability,
  ChatInput,
  ChatOutput,
  ChatStreamEvent,
  ProviderCaps,
} from '../../capability.js'
import { readSse } from '../../sse.js'

/** 上游错误，已归一成统一错误码 */
export class UpstreamError extends Error {
  constructor(
    readonly code: ErrorCode,
    readonly status: number | undefined,
    message: string,
  ) {
    super(message)
    this.name = 'UpstreamError'
  }
}

interface OpenAiUsage {
  prompt_tokens?: number
  completion_tokens?: number
  prompt_cache_hit_tokens?: number
  prompt_cache_miss_tokens?: number
  prompt_cache_write_tokens?: number
  completion_thinking_tokens?: number
  completion_tokens_details?: { reasoning_tokens?: number }
  prompt_tokens_details?: { cached_tokens?: number }
  service_tier?: string
}

interface OpenAiChoice {
  message?: { content?: string | null; reasoning_content?: string | null }
  finish_reason?: string
}

interface OpenAiResponse {
  model?: string
  choices?: OpenAiChoice[]
  usage?: OpenAiUsage
  error?: { message?: string; code?: string }
}

/** 流式分片。delta 里 content 与 reasoning_content 互不影响。 */
interface OpenAiChunk {
  model?: string
  choices?: Array<{
    index?: number
    delta?: { role?: string; content?: string | null; reasoning_content?: string | null }
    finish_reason?: string | null
  }>
  usage?: OpenAiUsage
  error?: { message?: string }
}

export interface OpenAiChatOptions {
  provider: string
  baseURL: string
  apiKey: string
  defaultModel: string
  timeoutMs?: number
}

const normalizeUsage = (u: OpenAiUsage | undefined): UsageSummary => {
  if (!u) return emptyUsage()
  const base = emptyUsage()
  return {
    ...base,
    inputTokens: u.prompt_tokens ?? 0,
    outputTokens: u.completion_tokens ?? 0,
    thinkingTokens:
      u.completion_tokens_details?.reasoning_tokens ?? u.completion_thinking_tokens ?? 0,
    cacheHitTokens: u.prompt_cache_hit_tokens ?? u.prompt_tokens_details?.cached_tokens ?? 0,
    cacheMissTokens: u.prompt_cache_miss_tokens ?? 0,
    cacheWriteTokens: u.prompt_cache_write_tokens ?? 0,
    ...(u.service_tier ? { serviceTier: u.service_tier } : {}),
    raw: u,
  }
}

const mapFinishReason = (reason: string | undefined): ChatOutput['finishReason'] => {
  switch (reason) {
    case 'length':
      return 'max_tokens'
    case 'tool_calls':
      return 'tool_use'
    case 'content_filter':
      return 'refusal'
    default:
      return 'end_turn'
  }
}

/** 把 canonical blocks 压成 OpenAI 的纯文本消息体（P0 阶段不带文件块） */
const toOpenAiMessages = (input: ChatInput): Array<{ role: string; content: string }> => {
  const out: Array<{ role: string; content: string }> = []
  if (input.system) out.push({ role: 'system', content: input.system })
  for (const m of input.messages) {
    const text = m.blocks
      .filter((b): b is Extract<ContentBlock, { type: 'text' }> => b.type === 'text')
      .map((b) => b.text)
      .join('')
    if (text) out.push({ role: m.role, content: text })
  }
  return out
}

/**
 * OpenAI 兼容 chat provider。
 * 同时覆盖 zhihui 聚合网关、DeepSeek 官方 /chat/completions、以及任何 OpenAI 兼容端点。
 */
export class OpenAiChatCapability implements ChatCapability {
  readonly kind = 'chat' as const
  readonly protocol = 'openai' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #defaultModel: string
  readonly #timeoutMs: number

  constructor(opts: OpenAiChatOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.chat`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    this.#defaultModel = opts.defaultModel
    this.#timeoutMs = opts.timeoutMs ?? 120_000
  }

  caps(): ProviderCaps {
    return {
      features: ['stream', 'thinking', 'usage-cache'],
      limits: { timeoutMs: this.#timeoutMs },
    }
  }

  #signal(ctx: CallCtx): AbortSignal {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    return ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout
  }

  #body(input: ChatInput, ctx: CallCtx, stream: boolean): string {
    const body: Record<string, unknown> = {
      model: input.model ?? this.#defaultModel,
      messages: toOpenAiMessages(input),
      stream,
    }
    if (input.maxTokens !== undefined) body.max_tokens = input.maxTokens
    if (input.temperature !== undefined) body.temperature = input.temperature
    if (input.thinking) {
      body.reasoning_effort = input.thinking.enabled ? (input.thinking.effort ?? 'high') : 'none'
    }
    // 多租户隔离：上游据此做内容安全与 KVCache 分离
    if (ctx.isolationKey) body.user = ctx.isolationKey
    return JSON.stringify(body)
  }

  async complete(input: ChatInput, ctx: CallCtx): Promise<ChatOutput> {
    const res = await fetch(`${this.#baseURL}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.#apiKey}`,
      },
      body: this.#body(input, ctx, false),
      signal: this.#signal(ctx),
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      let message = text || res.statusText
      try {
        const parsed = JSON.parse(text) as OpenAiResponse
        if (parsed.error?.message) message = parsed.error.message
      } catch {
        /* 非 JSON 响应体直接透出原文 */
      }
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, message)
    }

    const data = (await res.json()) as OpenAiResponse
    const choice = data.choices?.[0]
    const msg = choice?.message ?? {}

    const blocks: ContentBlock[] = []
    if (msg.reasoning_content) blocks.push({ type: 'reasoning', text: msg.reasoning_content })
    if (msg.content) blocks.push({ type: 'text', text: msg.content })

    return {
      blocks,
      usage: normalizeUsage(data.usage),
      finishReason: mapFinishReason(choice?.finish_reason),
      provider: this.provider,
      model: data.model ?? input.model ?? this.#defaultModel,
    }
  }

  async *stream(input: ChatInput, ctx: CallCtx): AsyncIterable<ChatStreamEvent> {
    const model = input.model ?? this.#defaultModel
    const res = await fetch(`${this.#baseURL}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: `Bearer ${this.#apiKey}`,
      },
      body: this.#body(input, ctx, true),
      signal: this.#signal(ctx),
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      let message = text || res.statusText
      try {
        const parsed = JSON.parse(text) as OpenAiResponse
        if (parsed.error?.message) message = parsed.error.message
      } catch {
        /* 非 JSON 响应体直接透出原文 */
      }
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, message)
    }
    if (!res.body) {
      throw new UpstreamError('protocol_violation', res.status, 'upstream returned no body')
    }

    yield { type: 'start', provider: this.provider, model }

    let nextIndex = 0
    let textIndex = -1
    let reasoningIndex = -1
    let finish: ChatOutput['finishReason'] = 'end_turn'
    let sawFinish = false
    let sawDone = false
    let usage: UsageSummary = emptyUsage()

    for await (const frame of readSse(res.body, ctx.signal)) {
      if (frame.data === '[DONE]') {
        sawDone = true
        break
      }

      let chunk: OpenAiChunk
      try {
        chunk = JSON.parse(frame.data) as OpenAiChunk
      } catch {
        // 不可解析的帧跳过，不影响后续有效内容
        continue
      }

      if (chunk.error?.message) {
        throw new UpstreamError('upstream_error', undefined, chunk.error.message)
      }
      if (chunk.usage) usage = normalizeUsage(chunk.usage)

      const choice = chunk.choices?.[0]
      if (!choice) continue
      const delta = choice.delta

      if (delta?.reasoning_content) {
        if (reasoningIndex < 0) {
          reasoningIndex = nextIndex++
          yield {
            type: 'block_start',
            index: reasoningIndex,
            block: { type: 'reasoning', text: '' },
          }
        }
        yield { type: 'reasoning_delta', index: reasoningIndex, text: delta.reasoning_content }
      }

      if (delta?.content) {
        if (textIndex < 0) {
          textIndex = nextIndex++
          yield { type: 'block_start', index: textIndex, block: { type: 'text', text: '' } }
        }
        yield { type: 'text_delta', index: textIndex, text: delta.content }
      }

      if (choice.finish_reason) {
        finish = mapFinishReason(choice.finish_reason)
        sawFinish = true
      }
    }

    // 既没有 finish_reason 也没有 [DONE]，说明流被截断
    if (!sawFinish && !sawDone) {
      throw new UpstreamError(
        'stream_interrupted',
        undefined,
        'stream ended without finish_reason or [DONE]',
      )
    }

    yield { type: 'usage', usage }
    yield { type: 'finish', finishReason: finish }
  }

  /** 把流式事件累积成完整结果，供不想自己拼装的调用方使用 */
  async streamToOutput(input: ChatInput, ctx: CallCtx): Promise<ChatOutput> {
    const blocks: ContentBlock[] = []
    let usage = emptyUsage()
    let finishReason: ChatOutput['finishReason'] = 'end_turn'
    const model = input.model ?? this.#defaultModel

    for await (const ev of this.stream(input, ctx)) {
      switch (ev.type) {
        case 'block_start':
          blocks[ev.index] = ev.block
          break
        case 'reasoning_delta':
        case 'text_delta': {
          const idx = ev.index
          const existing = blocks[idx]
          const kind = ev.type === 'text_delta' ? 'text' : 'reasoning'
          if (existing && existing.type === kind) {
            blocks[idx] = { ...existing, text: existing.text + ev.text }
          } else {
            blocks[idx] = { type: kind, text: ev.text }
          }
          break
        }
        case 'usage':
          usage = ev.usage
          break
        case 'finish':
          finishReason = ev.finishReason
          break
        case 'start':
          break
      }
    }

    return {
      blocks: blocks.filter((b): b is ContentBlock => Boolean(b)),
      usage,
      finishReason,
      provider: this.provider,
      model,
    }
  }
}
