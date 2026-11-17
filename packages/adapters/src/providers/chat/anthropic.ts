import {
  emptyUsage,
  mapUpstreamStatus,
  type Citation,
  type ContentBlock,
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
import { UpstreamError } from './openai.js'

/**
 * Anthropic Messages 协议（DeepSeek 的 /anthropic/v1/messages）。
 *
 * 这是唯一支持"全家桶"内建工具的通道：deepseek_web_search / deepseek_read_url
 * 由服务端执行，客户端不跑工具。chat-demo 用的就是这套，它也是本文件的契约来源。
 */

interface AnthropicUsage {
  input_tokens?: number
  output_tokens?: number
  cache_creation_input_tokens?: number
  cache_read_input_tokens?: number
  server_tool_use?: {
    web_search_requests?: number
    web_fetch_requests?: number
  }
}

interface AnthropicStreamEvent {
  type?: string
  index?: number
  message?: { id?: string; model?: string; usage?: AnthropicUsage }
  content_block?: { type?: string; text?: string; thinking?: string }
  delta?: {
    type?: string
    text?: string
    thinking?: string
    stop_reason?: string
    title?: string
    signature?: string
    citation?: { url?: string; title?: string | null; cited_text?: string }
  }
  usage?: AnthropicUsage
  error?: { message?: string }
}

const normalizeUsage = (u: AnthropicUsage | undefined): UsageSummary => {
  const base = emptyUsage()
  if (!u) return base
  return {
    ...base,
    inputTokens: u.input_tokens ?? 0,
    outputTokens: u.output_tokens ?? 0,
    cacheWriteTokens: u.cache_creation_input_tokens ?? 0,
    cacheHitTokens: u.cache_read_input_tokens ?? 0,
    searchCalls: u.server_tool_use?.web_search_requests ?? 0,
    fetchCalls: u.server_tool_use?.web_fetch_requests ?? 0,
    raw: u,
  }
}

const mapStopReason = (reason: string | undefined): ChatOutput['finishReason'] => {
  switch (reason) {
    case 'max_tokens':
      return 'max_tokens'
    case 'tool_use':
      return 'tool_use'
    case 'refusal':
      return 'refusal'
    default:
      return 'end_turn'
  }
}

/**
 * 把 canonical 块压成 Anthropic 的消息体。
 * 正文之外还支持 file_id 引用（图片走 image，文档走 document），
 * 这是只有 Anthropic 兼容通道才有的能力。
 */
const toAnthropicMessages = (input: ChatInput): Array<{ role: string; content: unknown }> => {
  const out: Array<{ role: string; content: unknown }> = []
  for (const m of input.messages) {
    if (m.role === 'system') continue

    const parts: unknown[] = []
    let plain = ''
    let hasRef = false

    for (const b of m.blocks) {
      if (b.type === 'text') {
        plain += b.text
      } else if (b.type === 'reasoning' && b.signature) {
        // 只有带签名的思考块才能回传：上游会用签名校验，缺签名直接 400
        parts.push({ type: 'thinking', thinking: b.text, signature: b.signature })
        hasRef = true
      } else if (b.type === 'image') {
        parts.push({ type: 'image', source: { type: 'file', file_id: b.fileId } })
        hasRef = true
      } else if (b.type === 'file') {
        parts.push({ type: 'document', source: { type: 'file', file_id: b.fileId } })
        hasRef = true
      }
    }

    if (plain) parts.push({ type: 'text', text: plain })
    if (parts.length === 0) continue

    out.push({ role: m.role === 'assistant' ? 'assistant' : 'user', content: hasRef ? parts : plain })
  }
  return out
}

const hasFileRef = (input: ChatInput): boolean =>
  input.messages.some((m) => m.blocks.some((b) => b.type === 'file' || b.type === 'image'))

export interface AnthropicChatOptions {
  provider: string
  baseURL: string
  apiKey: string
  defaultModel: string
  timeoutMs?: number
}

export class AnthropicChatCapability implements ChatCapability {
  readonly kind = 'chat' as const
  readonly protocol = 'anthropic' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #defaultModel: string
  readonly #timeoutMs: number

  constructor(opts: AnthropicChatOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.chat`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    this.#defaultModel = opts.defaultModel
    this.#timeoutMs = opts.timeoutMs ?? 180_000
  }

  caps(): ProviderCaps {
    return {
      // 这几个特性只有 anthropic 通道有
      features: ['stream', 'thinking', 'server-tools', 'server-title', 'safety-check'],
      limits: { timeoutMs: this.#timeoutMs },
    }
  }

  #signal(ctx: CallCtx): AbortSignal {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    return ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout
  }

  #body(input: ChatInput, stream: boolean): string {
    const body: Record<string, unknown> = {
      model: input.model ?? this.#defaultModel,
      max_tokens: input.maxTokens ?? 2048,
      messages: toAnthropicMessages(input),
      stream,
      safety_check: { level: 'high' },
    }
    if (input.system) body.system = input.system
    if (input.thinking?.enabled) {
      body.thinking = { type: 'enabled' }
      if (input.thinking.effort) body.reasoning_effort = input.thinking.effort
    } else {
      body.thinking = { type: 'disabled' }
    }
    // 服务端联网工具：由 DeepSeek 侧执行，客户端不跑
    if (input.tools?.length) {
      body.tools = input.tools.map((name) => ({ type: name, name }))
    }
    return JSON.stringify(body)
  }

  async complete(input: ChatInput, ctx: CallCtx): Promise<ChatOutput> {
    const collected: ContentBlock[] = []
    let usage = emptyUsage()
    let finishReason: ChatOutput['finishReason'] = 'end_turn'
    let model = input.model ?? this.#defaultModel

    for await (const ev of this.stream(input, ctx)) {
      switch (ev.type) {
        case 'start':
          model = ev.model
          break
        case 'block_start':
          collected[ev.index] = ev.block
          break
        case 'text_delta':
        case 'reasoning_delta': {
          const kind = ev.type === 'text_delta' ? 'text' : 'reasoning'
          const cur = collected[ev.index]
          if (cur && cur.type === kind) {
            collected[ev.index] = { ...cur, text: cur.text + ev.text } as ContentBlock
          } else {
            collected[ev.index] = { type: kind, text: ev.text } as ContentBlock
          }
          break
        }
        case 'usage':
          usage = ev.usage
          break
        case 'citations':
          // complete() 不把引用塞进 blocks，调用方按需从 stream 拿
          break
        case 'signature':
          // 同上：complete() 不重建思考签名
          break
        case 'finish':
          finishReason = ev.finishReason
          break
      }
    }

    return {
      blocks: collected.filter((b): b is ContentBlock => Boolean(b)),
      usage,
      finishReason,
      provider: this.provider,
      model,
    }
  }

  async *stream(input: ChatInput, ctx: CallCtx): AsyncIterable<ChatStreamEvent> {
    const res = await fetch(`${this.#baseURL}/anthropic/v1/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        // Anthropic 兼容层用 x-api-key，不是 Bearer
        'x-api-key': this.#apiKey,
        'anthropic-version': '2023-06-01',
        // 引用 Files API 上传的文件时必须带这个 beta 标记
        ...(hasFileRef(input) ? { 'anthropic-beta': 'files-api-2025-04-14' } : {}),
      },
      body: this.#body(input, true),
      signal: this.#signal(ctx),
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      let message = text || res.statusText
      try {
        const parsed = JSON.parse(text) as { error?: { message?: string } }
        if (parsed.error?.message) message = parsed.error.message
      } catch {
        /* 非 JSON 直接透出原文 */
      }
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, message)
    }
    if (!res.body) {
      throw new UpstreamError('protocol_violation', res.status, 'upstream returned no body')
    }

    let usage = emptyUsage()
    let finishReason: ChatOutput['finishReason'] = 'end_turn'
    let sawStop = false
    let started = false
    let nextIndex = 0
    const blockIndexOf = new Map<number, number>()
    const citations: Citation[] = []

    for await (const frame of readSse(res.body, ctx.signal)) {
      let evt: AnthropicStreamEvent
      try {
        evt = JSON.parse(frame.data) as AnthropicStreamEvent
      } catch {
        continue
      }

      if (evt.type === 'error') {
        throw new UpstreamError('upstream_error', undefined, evt.error?.message ?? 'stream error')
      }

      switch (evt.type) {
        case 'message_start': {
          const model = evt.message?.model ?? input.model ?? this.#defaultModel
          if (evt.message?.usage) usage = normalizeUsage(evt.message.usage)
          started = true
          yield { type: 'start', provider: this.provider, model }
          break
        }
        case 'content_block_start': {
          const kind = evt.content_block?.type
          // 工具块只承载工具信息，正文由 text 块给出。
          // 映射成空 text 块会在消息里留下一串 0 字符的块。
          if (
            kind === 'server_tool_use' ||
            kind === 'web_search_tool_result' ||
            kind === 'web_fetch_tool_result'
          ) {
            blockIndexOf.set(evt.index ?? 0, -1)
            break
          }
          const idx = nextIndex++
          blockIndexOf.set(evt.index ?? 0, idx)
          yield {
            type: 'block_start',
            index: idx,
            block:
              kind === 'thinking' ? { type: 'reasoning', text: '' } : { type: 'text', text: '' },
          }
          break
        }
        case 'content_block_delta': {
          const mapped = blockIndexOf.get(evt.index ?? 0)
          if (mapped === undefined || mapped < 0) break
          const d = evt.delta
          if (d?.type === 'text_delta' && d.text) {
            yield { type: 'text_delta', index: mapped, text: d.text }
          } else if (d?.type === 'thinking_delta' && d.thinking) {
            yield { type: 'reasoning_delta', index: mapped, text: d.thinking }
          } else if (d?.type === 'signature_delta' && d.signature) {
            // 思考块的签名在块结束前下发，必须存下来供下一轮回传
            yield { type: 'signature', index: mapped, signature: d.signature }
          } else if (d?.type === 'citations_delta' && d.citation?.url) {
            // 引用按 url 去重，序号按首次出现顺序编
            if (!citations.some((c) => c.url === d.citation?.url)) {
              citations.push({
                url: d.citation.url,
                title: d.citation.title ?? null,
                ...(d.citation.cited_text ? { citedText: d.citation.cited_text } : {}),
                index: citations.length + 1,
              })
            }
          }
          // signature_delta / input_json_delta 本轮不用
          break
        }
        case 'message_delta': {
          if (evt.usage) usage = { ...normalizeUsage(evt.usage), inputTokens: usage.inputTokens }
          if (evt.delta?.stop_reason) finishReason = mapStopReason(evt.delta.stop_reason)
          break
        }
        case 'message_stop':
          sawStop = true
          break
        case 'ping':
          break
      }

      if (sawStop) break
    }

    if (!started) {
      throw new UpstreamError('protocol_violation', undefined, 'no message_start received')
    }
    if (!sawStop) {
      throw new UpstreamError('stream_interrupted', undefined, 'stream ended without message_stop')
    }

    if (citations.length > 0) yield { type: 'citations', citations }
    yield { type: 'usage', usage }
    yield { type: 'finish', finishReason }
  }
}
