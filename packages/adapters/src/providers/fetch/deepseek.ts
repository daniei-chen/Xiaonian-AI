import type { FetchedPage } from '@xiaonian/protocol'
import { mapUpstreamStatus } from '@xiaonian/protocol'
import type { CallCtx, FetchCapability, ProviderCaps } from '../../capability.js'
import { UpstreamError } from '../chat/openai.js'
import type { DeepSeekToolOptions } from '../search/deepseek.js'

/**
 * 读链接。
 *
 * DeepSeek 没有单独的 read_url 端点（那是对话里的服务端工具名），
 * 独立调用就是 /v1/tools/fetch，返回已经转好的 markdown。
 */
export class DeepSeekFetchCapability implements FetchCapability {
  readonly kind = 'fetch' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #timeoutMs: number

  constructor(opts: DeepSeekToolOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.fetch`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    this.#timeoutMs = opts.timeoutMs ?? 45_000
  }

  caps(): ProviderCaps {
    return { features: ['markdown'], limits: { timeoutMs: this.#timeoutMs } }
  }

  async fetch(url: string, ctx: CallCtx): Promise<FetchedPage> {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    const signal = ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout

    const res = await fetch(`${this.#baseURL}/v1/tools/fetch`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.#apiKey}`,
      },
      body: JSON.stringify({ url }),
      signal,
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, text.slice(0, 300))
    }

    const data = (await res.json()) as {
      content?: string
      title?: string | null
      date?: string | null
    }

    return {
      url,
      title: data.title ?? null,
      publishedAt: data.date ?? null,
      markdown: data.content ?? '',
    }
  }
}
