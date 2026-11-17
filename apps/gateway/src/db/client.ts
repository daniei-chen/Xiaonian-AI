import { PGlite } from '@electric-sql/pglite'
import { mkdir } from 'node:fs/promises'

/**
 * P0 先跑 PGlite（WASM 版 PostgreSQL，零安装）。
 * 接真 PG 时替换本文件的实现即可，上层只用 query/exec 两个方法。
 */
let instance: PGlite | null = null

export const getDb = async (dataDir: string): Promise<PGlite> => {
  if (!instance) {
    // PGlite 的 nodefs 只做单层 mkdir，父目录要先建好
    await mkdir(dataDir, { recursive: true })
    instance = new PGlite(dataDir)
    await instance.waitReady
  }
  return instance
}

export const closeDb = async (): Promise<void> => {
  if (instance) {
    await instance.close()
    instance = null
  }
}

export const newId = (): string => crypto.randomUUID()

export { PGlite }
