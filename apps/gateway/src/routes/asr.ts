import type { FastifyInstance } from 'fastify'
import { apiError, mapUpstreamStatus } from '@xiaonian/protocol'
import { apiKey } from '../env.js'

export interface AsrRoutesDeps {
  /** 例如 https://api.deepseek.com */
  baseURL: string
}

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

interface AsrResponse {
  text?: string
  duration_ms?: number
  request_id?: string
}

/**
 * 语音输入的后端代理。
 *
 * 客户端只发一段 WAV，密钥留在服务端。DeepSeek 的 ASR 是整段上传、
 * 一次返回全文，没有流式 partial —— 所以这里也没有 partial。
 */
export const registerAsrRoutes = (app: LooseFastify, { baseURL }: AsrRoutesDeps): void => {
  // 音频走 raw body，不走 JSON
  for (const type of ['audio/wav', 'audio/x-wav', 'audio/ogg', 'application/octet-stream']) {
    app.addContentTypeParser(type, { parseAs: 'buffer' }, (_req, body, done) => {
      done(null, body)
    })
  }

  app.post('/api/v1/asr', { bodyLimit: 12 * 1024 * 1024 }, async (req, reply) => {
    const traceId = String(req.id)
    const key = apiKey('DEEPSEEK_API_KEY')
    if (!key) {
      return reply.code(503).send(apiError('internal', 'DEEPSEEK_API_KEY missing', traceId))
    }

    const body = req.body
    if (!Buffer.isBuffer(body) || body.length === 0) {
      return reply.code(400).send(apiError('bad_request', 'audio body required', traceId))
    }

    const contentType = String(req.headers['content-type'] ?? 'audio/wav')
    const filename = contentType.includes('ogg') ? 'recording.ogg' : 'recording.wav'

    const form = new FormData()
    form.append('file', new Blob([new Uint8Array(body)], { type: contentType }), filename)

    let res: Response
    try {
      res = await fetch(`${baseURL.replace(/\/+$/, '')}/v1/tools/asr`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${key}` },
        body: form,
      })
    } catch (err) {
      req.log.warn({ err }, 'asr upstream unreachable')
      return reply.code(502).send(apiError('network', 'asr upstream unreachable', traceId))
    }

    if (!res.ok) {
      const text = await res.text().catch(() => '')
      req.log.warn({ status: res.status, body: text.slice(0, 200) }, 'asr upstream error')
      return reply
        .code(res.status < 500 ? res.status : 502)
        .send(apiError(mapUpstreamStatus(res.status), text.slice(0, 300) || 'asr failed', traceId))
    }

    const data = (await res.json()) as AsrResponse
    return {
      text: data.text ?? '',
      durationMs: data.duration_ms ?? 0,
    }
  })
}
