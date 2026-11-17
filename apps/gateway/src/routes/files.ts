import type { FastifyInstance } from 'fastify'
import type { CallCtx, CapabilityRegistry } from '@xiaonian/adapters'
import { apiError } from '@xiaonian/protocol'

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

export interface FileRoutesDeps {
  registry: CapabilityRegistry
  userId: string
  provider: string | null
}

/**
 * 文件上传代理。
 *
 * 客户端发原始字节，真实类型走 X-Mime 头——这样只需一个 application/octet-stream
 * 解析器。purpose 规则在 provider 里，路由不管这些。
 */
export const registerFileRoutes = (
  app: LooseFastify,
  { registry, userId, provider }: FileRoutesDeps,
): void => {
  app.post('/api/v1/files', { bodyLimit: 64 * 1024 * 1024 }, async (req, reply) => {
    const traceId = String(req.id)
    if (!provider) {
      return reply.code(503).send(apiError('internal', 'no files provider configured', traceId))
    }

    const body = req.body
    if (!Buffer.isBuffer(body) || body.length === 0) {
      return reply.code(400).send(apiError('bad_request', 'file body required', traceId))
    }

    const mime = String(req.headers['x-mime'] ?? 'application/octet-stream').trim()
    const filename = decodeURIComponent(String(req.headers['x-filename'] ?? 'upload.bin'))
    const ctx: CallCtx = { userId, traceId, isolationKey: userId }

    try {
      const uploaded = await registry.files(provider).upload(
        { data: new Uint8Array(body), filename, mime },
        ctx,
      )
      return {
        fileId: uploaded.providerFileId,
        name: uploaded.name,
        mime: uploaded.mime,
        size: uploaded.size,
      }
    } catch (err) {
      req.log.warn({ err }, 'file upload failed')
      return reply.code(502).send(apiError('upstream_error', String(err), traceId))
    }
  })
}
