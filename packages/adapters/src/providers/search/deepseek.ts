import type { SearchResult } from '@xiaonian/protocol'
import { mapUpstreamStatus } from '@xiaonian/protocol'
import type { CallCtx, ProviderCaps, SearchCapability, SearchInput } from '../../capability.js'
import { UpstreamError } from '../chat/openai.js'

interface RawResult {
  title?: string
  url?: string
  snippet?: string
  summary?: string
  date?: string | null
  site_name?: string | null
  site_icon?: string
  authority?: number
  rerank_score?: number
  content?: string
}

export interface DeepSeekToolOptions {
  provider: string
  baseURL: string
  apiKey: string
  timeoutMs?: number
}

/**
 * DeepSeek 搜索。
 *
 * 这是**独立于对话的服务端接口**（/v1/tools/search），不是 messages 里的
 * server-side tool。区别很重要：前者我们拿得到结构化结果、能自己决定
 * 搜几轮、能把来源写进引用；后者完全在 DeepSeek 侧执行，我们只看到模型
 * 的输出。
 */
export class DeepSeekSearchCapability implements SearchCapability {
  readonly kind = 'search' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #timeoutMs: number

  constructor(opts: DeepSeekToolOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.search`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    this.#timeoutMs = opts.timeoutMs ?? 30_000
  }

  caps(): ProviderCaps {
    return { features: ['structured'], limits: { timeoutMs: this.#timeoutMs } }
  }

  async search(input: SearchInput, ctx: CallCtx): Promise<SearchResult[]> {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    const signal = ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout

    const res = await fetch(`${this.#baseURL}/v1/tools/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.#apiKey}`,
      },
      body: JSON.stringify({
        query: input.query,
        count: input.count ?? 8,
        ...(input.needContent ? { need_content: true } : {}),
      }),
      signal,
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, text.slice(0, 300))
    }

    const data = (await res.json()) as { results?: RawResult[] }
    return (data.results ?? [])
      .filter((r): r is RawResult & { url: string } => typeof r.url === 'string' && r.url.length > 0)
      .map((r) => ({
        url: r.url,
        title: r.title ?? r.url,
        // summary 是整篇正文，snippet 才是给模型看的短摘要
        snippet: r.snippet?.trim() || (r.summary ?? '').slice(0, 400),
        publishedAt: r.date ?? null,
        siteName: r.site_name ?? null,
        siteIcon: r.site_icon,
        raw: { authority: r.authority, rerankScore: r.rerank_score },
      }))
  }
}
