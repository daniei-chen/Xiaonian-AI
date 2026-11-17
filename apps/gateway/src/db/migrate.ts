import { readdir, readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { loadEnv } from '../env.js'
import { getDb } from './client.js'

const MIGRATIONS_TABLE = `
CREATE TABLE IF NOT EXISTS schema_migrations (
  version text PRIMARY KEY,
  applied_at timestamptz NOT NULL DEFAULT now()
)`

/** 按文件名顺序应用 infra/migrations/*.sql，已应用的跳过 */
export const runMigrations = async (rootDir: string, dataDir: string): Promise<string[]> => {
  const db = await getDb(dataDir)
  await db.exec(MIGRATIONS_TABLE)

  const dir = resolve(rootDir, 'infra/migrations')
  const files = (await readdir(dir)).filter((f) => f.endsWith('.sql')).sort()

  const done = await db.query<{ version: string }>('SELECT version FROM schema_migrations')
  const applied = new Set(done.rows.map((r) => r.version))

  const newly: string[] = []
  for (const file of files) {
    if (applied.has(file)) continue
    const sql = await readFile(resolve(dir, file), 'utf8')
    await db.exec(sql)
    await db.query('INSERT INTO schema_migrations (version) VALUES ($1)', [file])
    newly.push(file)
  }
  return newly
}

// 作为脚本执行：pnpm db:migrate
const env = loadEnv()
const applied = await runMigrations(env.rootDir, env.dataDir)

if (applied.length === 0) {
  console.log('[migrate] already up to date')
} else {
  console.log(`[migrate] applied ${applied.length} migration(s):`)
  for (const f of applied) console.log(`  - ${f}`)
}
process.exit(0)
