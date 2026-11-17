import type { FastifyInstance } from 'fastify'
import { apiError, mapUpstreamStatus } from '@xiaonian/protocol'
import { apiKey } from '../env.js'

export interface FileRoutesDeps {
  baseURL: string
}

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

const IMAGE = /^image\//
const LAYOUT_DOC = /pdf|msword|wordprocessingml|presentationml|powerpoint/

/**
 * DeepSeek 的 purpose 只有三个，且规则与常规认知不同：
 *  - 图片：不带 purpose（带上反而会 400）
 *  - PDF / Word / PPT：rich_layout_recognition（要服务端做版面解析）
 *  - 其余文本/表格/电子书：user_data
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

/**
 * 文件上传代理。
 *
 * 客户端发原始字节，真实类型走 X-Mime 头——这样只需一个 application/octet-stream
 * 解析器，不用为每种 mime 注册 parser。
 * 上传到 DeepSeek 的 Anthropic 兼容 Files API，因为只有那条通道支持
 * 在消息里用 file_id 引用文档。
 */
export const registerFileRoutes = (app: LooseFastify, { baseURL }: FileRoutesDeps): void => {
  app.post('/api/v1/files', { bodyLimit: 64 * 1024 * 1024 }, async (req, reply) => {
    const traceId = String(req.id)
    const key = apiKey('DEEPSEEK_API_KEY')
    if (!key) {
      return reply.code(503).send(apiError('internal', 'DEEPSEEK_API_KEY missing', traceId))
    }

    const body = req.body
    if (!Buffer.isBuffer(body) || body.length === 0) {
      return reply.code(400).send(apiError('bad_request', 'file body required', traceId))
    }

    const mime = String(req.headers['x-mime'] ?? 'application/octet-stream').trim()
    const rawName = String(req.headers['x-filename'] ?? 'upload.bin')
    const filename = decodeURIComponent(rawName)
    const purpose = pickPurpose(mime)

    const form = new FormData()
    // 顺序有讲究：purpose 必须在 file 之前，否则服务端解析失败
    if (purpose) form.append('purpose', purpose)
    form.append('file', new Blob([new Uint8Array(body)], { type: mime }), filename)

    let res: Response
    try {
      res = await fetch(`${baseURL.replace(/\/+$/, '')}/anthropic/v1/files`, {
        method: 'POST',
        headers: {
          'x-api-key': key,
          'anthropic-version': '2023-06-01',
          'anthropic-beta': 'files-api-2025-04-14',
        },
        body: form,
      })
    } catch (err) {
      req.log.warn({ err }, 'file upstream unreachable')
      return reply.code(502).send(apiError('network', 'file upstream unreachable', traceId))
    }

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      req.log.warn({ status: res.status, body: text.slice(0, 200) }, 'file upstream error')
      return reply
        .code(res.status < 500 ? res.status : 502)
        .send(apiError(mapUpstreamStatus(res.status), text.slice(0, 300) || 'upload failed', traceId))
    }

    const data = (await res.json()) as FileObject
    if (!data.id) {
      return reply.code(502).send(apiError('protocol_violation', 'no file id returned', traceId))
    }

    return {
      fileId: data.id,
      name: data.filename ?? filename,
      mime: data.mime_type ?? mime,
      size: data.size_bytes ?? body.length,
      purpose,
    }
  })
}
