import { mapUpstreamStatus } from '@xiaonian/protocol'
import type { CallCtx, FilesCapability, ProviderCaps, UploadedFile } from '../../capability.js'
import { UpstreamError } from '../chat/openai.js'
import type { DeepSeekToolOptions } from '../search/deepseek.js'

const IMAGE = /^image\//
const LAYOUT_DOC = /pdf|msword|wordprocessingml|presentationml|powerpoint/

/**
 * 附件上传。
 *
 * purpose 规则反直觉，踩过：
 *  - 图片：**不能**带 purpose，带上反而 400
 *  - PDF / Word / PPT：rich_layout_recognition（服务端做版面解析）
 *  - 其余：user_data
 * 且 purpose 必须排在 file 之前，顺序错了解析失败。
 */
const pickPurpose = (mime: string): string | null => {
  if (IMAGE.test(mime)) return null
  if (LAYOUT_DOC.test(mime)) return 'rich_layout_recognition'
  return 'user_data'
}

interface FileObject {
  id?: string
  filename?: string
  size_bytes?: number
  mime_type?: string
}

export class DeepSeekFilesCapability implements FilesCapability {
  readonly kind = 'files' as const
  readonly id: string
  readonly provider: string
  readonly #baseURL: string
  readonly #apiKey: string
  readonly #timeoutMs: number

  constructor(opts: DeepSeekToolOptions) {
    this.provider = opts.provider
    this.id = `${opts.provider}.files`
    this.#baseURL = opts.baseURL.replace(/\/+$/, '')
    this.#apiKey = opts.apiKey
    this.#timeoutMs = opts.timeoutMs ?? 120_000
  }

  caps(): ProviderCaps {
    return { features: ['file_id'], limits: { timeoutMs: this.#timeoutMs } }
  }

  #headers(): Record<string, string> {
    return {
      'x-api-key': this.#apiKey,
      'anthropic-version': '2023-06-01',
      'anthropic-beta': 'files-api-2025-04-14',
    }
  }

  async upload(
    input: { data: Uint8Array; filename: string; mime: string; purpose?: string },
    ctx: CallCtx,
  ): Promise<UploadedFile> {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    const signal = ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout

    const purpose = input.purpose ?? pickPurpose(input.mime)
    const form = new FormData()
    if (purpose) form.append('purpose', purpose)
    form.append(
      'file',
      new Blob([new Uint8Array(input.data)], { type: input.mime }),
      input.filename,
    )

    const res = await fetch(`${this.#baseURL}/anthropic/v1/files`, {
      method: 'POST',
      headers: this.#headers(),
      body: form,
      signal,
    })

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, text.slice(0, 300))
    }

    const data = (await res.json()) as FileObject
    if (!data.id) {
      throw new UpstreamError('protocol_violation', res.status, 'no file id returned')
    }

    return {
      provider: this.provider,
      providerFileId: data.id,
      name: data.filename ?? input.filename,
      mime: data.mime_type ?? input.mime,
      size: data.size_bytes ?? input.data.length,
    }
  }

  async remove(providerFileId: string, ctx: CallCtx): Promise<void> {
    const timeout = AbortSignal.timeout(this.#timeoutMs)
    const signal = ctx.signal ? AbortSignal.any([ctx.signal, timeout]) : timeout

    const res = await fetch(`${this.#baseURL}/anthropic/v1/files/${providerFileId}`, {
      method: 'DELETE',
      headers: this.#headers(),
      signal,
    })
    // 404 说明早就没了，删除应当是幂等的
    if (!res.ok && res.status !== 404) {
      throw new UpstreamError(mapUpstreamStatus(res.status), res.status, 'delete failed')
    }
  }
}
