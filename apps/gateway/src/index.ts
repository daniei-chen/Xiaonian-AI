import { buildApp } from './app.js'
import { buildCapabilities } from './capabilities.js'
import { closeDb, getDb } from './db/client.js'
import { loadEnv } from './env.js'
import { createLogger } from './logger.js'

const env = loadEnv()
const logger = createLogger(env.nodeEnv === 'development' ? 'debug' : 'info')

// 迁移由 pnpm db:migrate 单独执行，这里只确保能连上
const db = await getDb(env.dataDir)

const caps = await buildCapabilities(env)
if (!caps.defaultChatProvider) {
  logger.warn('no chat provider available — fill ZHIHUI_API_KEY / DEEPSEEK_API_KEY in .env')
}

const app = await buildApp({ env, logger, db, caps })

const shutdown = async (signal: string): Promise<void> => {
  logger.info({ signal }, 'shutting down')
  await app.close()
  await closeDb()
  process.exit(0)
}

process.on('SIGINT', () => void shutdown('SIGINT'))
process.on('SIGTERM', () => void shutdown('SIGTERM'))

await app.listen({ port: env.port, host: '0.0.0.0' })
logger.info(
  { port: env.port, dataDir: env.dataDir, chatProvider: caps.defaultChatProvider },
  'gateway listening',
)
