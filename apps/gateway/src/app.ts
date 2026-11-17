import cors from '@fastify/cors'
import type { PGlite } from '@electric-sql/pglite'
import { resolveChain } from '@xiaonian/adapters'
import { apiError, type ErrorCode } from '@xiaonian/protocol'
import Fastify, { type FastifyError } from 'fastify'
import type { Logger } from 'pino'
import type { CapabilitiesRuntime } from './capabilities.js'
import type { Env } from './env.js'
import { registerConversationRoutes } from './routes/conversations.js'

export interface AppDeps {
  env: Env
  logger: Logger
  db: PGlite
  caps: CapabilitiesRuntime
}

const statusToCode = (status: number): ErrorCode => {
  switch (status) {
    case 400:
      return 'bad_request'
    case 401:
      return 'unauthorized'
    case 403:
      return 'forbidden'
    case 404:
      return 'not_found'
    case 409:
      return 'conflict'
    case 429:
      return 'rate_limited'
    default:
      return status >= 500 ? 'internal' : 'bad_request'
  }
}

export const buildApp = async ({ env, logger, db, caps }: AppDeps) => {
  const app = Fastify({
    loggerInstance: logger,
    trustProxy: true,
    bodyLimit: 16 * 1024 * 1024,
  })

  await app.register(cors, { origin: true })

  app.get('/healthz', async () => ({
    ok: true,
    service: 'xiaonian-gateway',
    version: '0.0.0',
    env: env.nodeEnv,
    chatProvider: caps.defaultChatProvider,
    now: new Date().toISOString(),
  }))

  /**
   * 能力探测：客户端据此决定哪些按钮可用。
   * 只暴露 provider 名与调用链，不暴露密钥与 baseURL。
   */
  app.get('/api/v1/meta/capabilities', async () => ({
    chat: {
      providers: caps.chatProviders,
      default: caps.defaultChatProvider,
    },
    kinds: Object.fromEntries(
      Object.keys(caps.config).map((kind) => [kind, resolveChain(caps.config, kind)]),
    ),
  }))

  registerConversationRoutes(app, { db, caps })

  app.setNotFoundHandler((req, reply) => {
    reply.code(404).send(apiError('not_found', `no route: ${req.method} ${req.url}`, String(req.id)))
  })

  app.setErrorHandler((err: FastifyError, req, reply) => {
    const status = typeof err.statusCode === 'number' ? err.statusCode : 500
    req.log.error({ err }, 'request failed')
    reply.code(status).send(apiError(statusToCode(status), err.message, String(req.id)))
  })

  return app
}
