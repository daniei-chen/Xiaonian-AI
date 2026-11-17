import { buildApp } from './app.js'
import { closeDb, getDb } from './db/client.js'
import { loadEnv } from './env.js'
import { createLogger } from './logger.js'

const env = loadEnv()
const logger = createLogger(env.nodeEnv === 'development' ? 'debug' : 'info')

// 预热数据库：迁移由 pnpm db:migrate 单独执行，这里只确保能连上
await getDb(env.dataDir)

const app = await buildApp({ env, logger })

const shutdown = async (signal: string): Promise<void> => {
  logger.info({ signal }, 'shutting down')
  await app.close()
  await closeDb()
  process.exit(0)
}

process.on('SIGINT', () => void shutdown('SIGINT'))
process.on('SIGTERM', () => void shutdown('SIGTERM'))

await app.listen({ port: env.port, host: '0.0.0.0' })
logger.info({ port: env.port, dataDir: env.dataDir }, 'gateway listening')
