import type { FastifyInstance } from 'fastify'
import type { CallCtx, CapabilityRegistry } from '@xiaonian/adapters'
import { apiError } from '@xiaonian/protocol'

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

export interface TtsRoutesDeps {
  registry: CapabilityRegistry
  userId: string
  provider: string | null
}

interface TtsBody {
  text?: string
  voiceId?: string
  format?: 'wav' | 'mp3'
}

/**
 * 语音合成代理。
 *
 * 直接返回音频字节而不是上传到对象存储：语音对话场景要的是"立刻能播"。
 */
export const registerTtsRoutes = (
  app: LooseFastify,
  { registry, userId, provider }: TtsRoutesDeps,
): void => {
  app.post<{ Body: TtsBody }>('/api/v1/tts', async (req, reply) => {
    const traceId = String(req.id)
    if (!provider) {
      return reply.code(503).send(apiError('internal', 'no tts provider configured', traceId))
    }

    const text = (req.body?.text ?? '').trim()
    if (!text) {
      return reply.code(400).send(apiError('bad_request', 'text required', traceId))
    }

    const ctx: CallCtx = { userId, traceId, isolationKey: userId }

    try {
      const result = await registry.tts(provider).synthesize(
        {
          text,
          ...(req.body?.voiceId ? { voice: req.body.voiceId } : {}),
          format: req.body?.format ?? 'wav',
        },
        ctx,
      )

      return reply
        .header('Content-Type', result.mime)
        .header('Cache-Control', 'no-store')
        .send(Buffer.from(result.audio))
    } catch (err) {
      req.log.warn({ err }, 'tts failed')
      return reply.code(502).send(apiError('upstream_error', String(err), traceId))
    }
  })
}
