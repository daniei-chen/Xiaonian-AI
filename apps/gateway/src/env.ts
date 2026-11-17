import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { loadEnvFile } from 'node:process'

export interface Env {
  nodeEnv: string
  port: number
  /** 仓库根目录 */
  rootDir: string
  /** PGlite 数据目录（未接真 PG 时的兜底） */
  dataDir: string
  /** 预留：接真 PostgreSQL 时启用 */
  databaseUrl: string
  jwtSecret: string
}

const positiveInt = (raw: string | undefined, fallback: number): number => {
  const n = Number(raw)
  return Number.isFinite(n) && n > 0 ? Math.trunc(n) : fallback
}

export const loadEnv = (): Env => {
  // dist/env.js → apps/gateway/dist → 上三级即仓库根
  const rootDir = resolve(import.meta.dirname, '../../..')
  const envPath = resolve(rootDir, '.env')
  if (existsSync(envPath)) {
    try {
      loadEnvFile(envPath)
    } catch {
      // .env 不合法不该阻止启动，交给下游报明确的缺项错误
    }
  }

  return {
    nodeEnv: process.env.NODE_ENV ?? 'development',
    port: positiveInt(process.env.GATEWAY_PORT, 8787),
    rootDir,
    dataDir: process.env.XIAONIAN_DATA_DIR ?? resolve(rootDir, '.data/pg'),
    databaseUrl: process.env.DATABASE_URL ?? '',
    jwtSecret: process.env.JWT_SECRET ?? 'dev-only-change-me',
  }
}

/** 取上游密钥；缺失时返回 undefined，由调用方决定是降级还是报错 */
export const apiKey = (envName: string): string | undefined => {
  const v = process.env[envName]
  return v && v.trim() ? v.trim() : undefined
}
