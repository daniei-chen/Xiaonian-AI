import { mapUpstreamStatus } from '@xiaonian/protocol'
import type { CallCtx, OcrCapability, OcrInput, OcrPage, ProviderCaps } from '../../capability.js'
import { UpstreamError } from '../chat/openai.js'
import type { DeepSeekToolOptions } from '../search/deepseek.js'

interface OcrResponse {
  page_count?: number
  pages?: Array<{ index?: number; text?: string; finish_reason?: string }>
}

/**
 * OCR。
 *
 * 两种 purpose 语义不同，不是精度高低：
 *  - text_recognition：只要文字，输出纯文本
 *  - rich_layout_recognition：保留版面，输出 <|ref|><|box|>[[x1,y1,x2,y2]] 标记
 *    适合表格、公式、多栏文档
 */
export class DeepSeekOcrCapability implements OcrCapability {
  readonly kind = 'ocr' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #timeoutMs: number

  constructor(opts: DeepSeekToolOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.ocr`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    // OCR 是同步长任务，默认给足时间
    this.#timeoutMs = opts.timeoutMs ?? 120_000
  }

  caps(): ProviderCaps {
    return {
      features: ['text', 'layout'],
      limits: { timeoutMs: this.#timeoutMs },
    }
  }

  async ocr(input: OcrInput, ctx: CallCtx): Promise<OcrPage[]> {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    const signal = ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout

    const form = new FormData()
    form.append('purpose', input.purpose)
    form.append(
      'file',
      new Blob([new Uint8Array(input.data)], { type: input.mime }),
      input.filename,
    )

    const res = await fetch(`${this.#baseURL}/v1/tools/ocr`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.#apiKey}` },
      body: form,
      signal,
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, text.slice(0, 300))
    }

    const data = (await res.json()) as OcrResponse
    return (data.pages ?? []).map((page, i) => ({
      index: page.index ?? i,
      text: page.text ?? '',
    }))
  }
}
