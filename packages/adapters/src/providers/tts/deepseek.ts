import { mapUpstreamStatus } from '@xiaonian/protocol'
import type { CallCtx, ProviderCaps, TtsCapability, TtsInput, TtsResult } from '../../capability.js'
import { UpstreamError } from '../chat/openai.js'
import type { DeepSeekToolOptions } from '../search/deepseek.js'

/**
 * 语音合成。
 *
 * 直接给容器格式（wav/mp3）而不是裸 PCM：播放侧用 MediaPlayer 就能放，
 * 转 PCM 还得自己剥 RIFF 头，对客户端没有任何好处。
 */
export class DeepSeekTtsCapability implements TtsCapability {
  readonly kind = 'tts' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #timeoutMs: number

  constructor(opts: DeepSeekToolOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.tts`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    this.#timeoutMs = opts.timeoutMs ?? 90_000
  }

  caps(): ProviderCaps {
    return { features: ['wav', 'mp3'], limits: { timeoutMs: this.#timeoutMs } }
  }

  async synthesize(input: TtsInput, ctx: CallCtx): Promise<TtsResult> {
    const format = input.format ?? 'wav'
    // 上游只认 wav / mp3 / opus / pcm，pcm 在这里没有落地场景
    const upstreamFormat = format === 'pcm' ? 'wav' : format

    const timeout = AbortSignal.timeout(this.#timeoutMs)
    const signal = ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout

    const res = await fetch(`${this.#baseURL}/v1/tools/tts`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.#apiKey}`,
      },
      body: JSON.stringify({
        text: input.text.slice(0, 4000),
        voice_id: input.voice ?? 'stella',
        format: upstreamFormat,
      }),
      signal,
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, text.slice(0, 300))
    }

    return {
      audio: new Uint8Array(await res.arrayBuffer()),
      mime: upstreamFormat === 'wav' ? 'audio/wav' : 'audio/mpeg',
    }
  }
}
