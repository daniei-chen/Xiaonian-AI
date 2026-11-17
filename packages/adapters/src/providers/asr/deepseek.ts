import { mapUpstreamStatus } from '@xiaonian/protocol'
import type { AsrCapability, AsrInput, AsrResult, CallCtx, ProviderCaps } from '../../capability.js'
import { UpstreamError } from '../chat/openai.js'
import type { DeepSeekToolOptions } from '../search/deepseek.js'

/**
 * 语音识别。
 *
 * 整段上传、一次返回全文，没有流式 partial —— 上游就是这样，
 * 所以本地做"边说边出字"是假的，别在 UI 上假装有。
 */
export class DeepSeekAsrCapability implements AsrCapability {
  readonly kind = 'asr' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #timeoutMs: number

  constructor(opts: DeepSeekToolOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.asr`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    this.#timeoutMs = opts.timeoutMs ?? 120_000
  }

  caps(): ProviderCaps {
    return { features: ['batch'], limits: { timeoutMs: this.#timeoutMs } }
  }

  async transcribe(input: AsrInput, ctx: CallCtx): Promise<AsrResult> {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    const signal = ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout

    const form = new FormData()
    form.append(
      'file',
      new Blob([new Uint8Array(input.data)], { type: input.mime }),
      input.filename,
    )

    const res = await fetch(`${this.#baseURL}/v1/tools/asr`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.#apiKey}` },
      body: form,
      signal,
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, text.slice(0, 300))
    }

    const data = (await res.json()) as { text?: string; duration_ms?: number }
    return { text: data.text ?? '', durationMs: data.duration_ms ?? 0 }
  }
}
