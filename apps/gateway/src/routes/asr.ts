import type { FastifyInstance } from 'fastify'
import type { CallCtx, CapabilityRegistry } from '@xiaonian/adapters'
import { apiError } from '@xiaonian/protocol'

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

export interface AsrRoutesDeps {
  registry: CapabilityRegistry
  userId: string
  /** 默认 asr provider；null 表示没装配成功 */
  provider: string | null
}

/**
 * 语音输入的后端代理。
 *
 * 客户端只发一段 WAV，密钥留在服务端。走能力层而不是直连 DeepSeek，
 * 这样换 ASR 供应商不用改路由。
 */
export const registerAsrRoutes = (
  app: LooseFastify,
  { registry, userId, provider }: AsrRoutesDeps,
): void => {
  app.post('/api/v1/asr', { bodyLimit: 12 * 1024 * 1024 }, async (req, reply) => {
    const traceId = String(req.id)
    if (!provider) {
      return reply.code(503).send(apiError('internal', 'no asr provider configured', traceId))
    }

    const body = req.body
    if (!Buffer.isBuffer(body) || body.length === 0) {
      return reply.code(400).send(apiError('bad_request', 'audio body required', traceId))
    }

    const contentType = String(req.headers['content-type'] ?? 'audio/wav')
    const filename = contentType.includes('ogg') ? 'recording.ogg' : 'recording.wav'

    const ctx: CallCtx = { userId, traceId, isolationKey: userId }

    try {
      const result = await registry.asr(provider).transcribe(
        { data: new Uint8Array(body), filename, mime: contentType },
        ctx,
      )
      return { text: result.text, durationMs: result.durationMs }
    } catch (err) {
      req.log.warn({ err }, 'asr failed')
      return reply.code(502).send(apiError('upstream_error', String(err), traceId))
    }
  })
}
