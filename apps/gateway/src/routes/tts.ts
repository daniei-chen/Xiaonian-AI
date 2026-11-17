import type { FastifyInstance } from 'fastify'
import { apiError, mapUpstreamStatus } from '@xiaonian/protocol'
import { apiKey } from '../env.js'

export interface TtsRoutesDeps {
  baseURL: string
}

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

interface TtsBody {
  text?: string
  voiceId?: string
  format?: 'wav' | 'mp3' | 'opus' | 'pcm'
}

/**
 * 语音合成代理。
 *
 * 直接返回音频字节而不是上传到对象存储：语音对话场景要的是"立刻能播"，
 * 多一次落盘和一次下载没有意义。
 */
export const registerTtsRoutes = (app: LooseFastify, { baseURL }: TtsRoutesDeps): void => {
  app.post<{ Body: TtsBody }>('/api/v1/tts', async (req, reply) => {
    const traceId = String(req.id)
    const key = apiKey('DEEPSEEK_API_KEY')
    if (!key) {
      return reply.code(503).send(apiError('internal', 'DEEPSEEK_API_KEY missing', traceId))
    }

    const text = (req.body?.text ?? '').trim()
    if (!text) {
      return reply.code(400).send(apiError('bad_request', 'text required', traceId))
    }

    const format = req.body?.format ?? 'wav'
    let res: Response
    try {
      res = await fetch(`${baseURL.replace(/\/+$/, '')}/v1/tools/tts`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${key}`,
        },
        body: JSON.stringify({
          text: text.slice(0, 4000),
          voice_id: req.body?.voiceId ?? 'stella',
          format,
        }),
      })
    } catch (err) {
      req.log.warn({ err }, 'tts upstream unreachable')
      return reply.code(502).send(apiError('network', 'tts upstream unreachable', traceId))
    }

    if (!res.ok) {
      const message = await res.text().catch(() => '')
      req.log.warn({ status: res.status, body: message.slice(0, 200) }, 'tts upstream error')
      return reply
        .code(res.status < 500 ? res.status : 502)
        .send(
          apiError(mapUpstreamStatus(res.status), message.slice(0, 300) || 'tts failed', traceId),
        )
    }

    const audio = Buffer.from(await res.arrayBuffer())
    return reply
      .header('Content-Type', format === 'wav' ? 'audio/wav' : 'audio/mpeg')
      .header('Cache-Control', 'no-store')
      .send(audio)
  })
}
