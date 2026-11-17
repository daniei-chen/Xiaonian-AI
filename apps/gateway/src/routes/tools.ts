import type { FastifyInstance } from 'fastify'
import type { CallCtx, CapabilityRegistry } from '@xiaonian/adapters'
import { apiError } from '@xiaonian/protocol'

/** logger 泛型放开，才能接住 buildApp 里传入的 pino 实例 */
type LooseFastify = FastifyInstance<any, any, any, any, any>

export interface ToolRoutesDeps {
  registry: CapabilityRegistry
  userId: string
  defaults: {
    search: string | null
    fetch: string | null
    ocr: string | null
  }
}

/**
 * 工具类能力的 HTTP 出口：搜索、读链接、OCR。
 *
 * 这三个接口是为"客户端直接要结果"准备的（比如粘贴一个链接点解析、
 * 拍一张图直接取文字）。对话里的深度搜索走的是内部 loop，不经过这里。
 */
export const registerToolRoutes = (
  app: LooseFastify,
  { registry, userId, defaults }: ToolRoutesDeps,
) => {
  const ctxOf = (req: { id: unknown }): CallCtx => ({
    userId,
    traceId: String(req.id),
    isolationKey: 'default',
  })

  app.post<{ Body: { query?: string; count?: number; needContent?: boolean } }>(
    '/api/v1/search',
    async (req, reply) => {
      const traceId = String(req.id)
      const query = (req.body?.query ?? '').trim()
      if (!query) {
        return reply.code(400).send(apiError('bad_request', 'query required', traceId))
      }
      const provider = defaults.search
      if (!provider) {
        return reply.code(503).send(apiError('internal', 'no search provider configured', traceId))
      }

      try {
        const results = await registry.search(provider).search(
          {
            query,
            ...(req.body?.count !== undefined ? { count: req.body.count } : {}),
            ...(req.body?.needContent !== undefined ? { needContent: req.body.needContent } : {}),
          },
          ctxOf(req),
        )
        return { results }
      } catch (err) {
        req.log.warn({ err }, 'search failed')
        return reply.code(502).send(apiError('upstream_error', String(err), traceId))
      }
    },
  )

  app.post<{ Body: { url?: string } }>('/api/v1/fetch', async (req, reply) => {
    const traceId = String(req.id)
    const url = (req.body?.url ?? '').trim()
    if (!/^https?:\/\//i.test(url)) {
      return reply.code(400).send(apiError('bad_request', 'valid http(s) url required', traceId))
    }
    const provider = defaults.fetch
    if (!provider) {
      return reply.code(503).send(apiError('internal', 'no fetch provider configured', traceId))
    }

    try {
      return await registry.fetch(provider).fetch(url, ctxOf(req))
    } catch (err) {
      req.log.warn({ err, url }, 'fetch failed')
      return reply.code(502).send(apiError('upstream_error', String(err), traceId))
    }
  })

  app.post('/api/v1/ocr', { bodyLimit: 32 * 1024 * 1024 }, async (req, reply) => {
    const traceId = String(req.id)
    const body = req.body
    if (!Buffer.isBuffer(body) || body.length === 0) {
      return reply.code(400).send(apiError('bad_request', 'image body required', traceId))
    }
    const provider = defaults.ocr
    if (!provider) {
      return reply.code(503).send(apiError('internal', 'no ocr provider configured', traceId))
    }

    const mime = String(req.headers['x-mime'] ?? 'image/png').trim()
    const filename = decodeURIComponent(String(req.headers['x-filename'] ?? 'image.png'))
    const layout = String(req.headers['x-purpose'] ?? '') === 'layout'

    try {
      const pages = await registry.ocr(provider).ocr(
        {
          data: new Uint8Array(body),
          filename,
          mime,
          purpose: layout ? 'rich_layout_recognition' : 'text_recognition',
        },
        ctxOf(req),
      )
      // 合并多页，前端只关心整段文字
      return { text: pages.map((p) => p.text).join('\n\n'), pages }
    } catch (err) {
      req.log.warn({ err }, 'ocr failed')
      return reply.code(502).send(apiError('upstream_error', String(err), traceId))
    }
  })
}
