/**
 * v3 归档格式验证：结构、fragment 类型、往返回环。
 * 用法：node --env-file=.env scripts/e2e-archive.mjs
 */
import { unzip } from '../apps/gateway/dist/lib/zip.js'

const GW = process.env.XIAONIAN_BASE ?? 'http://127.0.0.1:8788'

// 1) 导出
const res = await fetch(`${GW}/api/v1/export`)
if (!res.ok) {
  console.error('export failed:', res.status)
  process.exit(1)
}
const zip = Buffer.from(await res.arrayBuffer())
const entries = unzip(zip)

const names = [...entries.keys()]
console.log(`zip: ${(zip.length / 1024).toFixed(1)} KB, ${names.length} entries`)
console.log('manifest first:', names[0] === 'manifest.json' ? 'YES' : `NO (${names[0]})`)

const manifest = JSON.parse(entries.get('manifest.json').toString('utf8'))
console.log('manifest:', JSON.stringify(manifest))

const sessionNames = names.filter((n) => n.startsWith('chat_sessions/'))
console.log(`sessions: ${sessionNames.length}`)
console.log(`index present: ${entries.has('chat_session_index.json') ? 'YES' : 'NO'}`)

// 2) 检查 fragment 类型覆盖
const fragmentTypes = new Set()
let messageTotal = 0
let sample = null

for (const name of sessionNames) {
  const session = JSON.parse(entries.get(name).toString('utf8'))
  messageTotal += session.messages?.length ?? 0
  for (const m of session.messages ?? []) {
    for (const f of m.fragments ?? []) fragmentTypes.add(f.type)
    if (!sample && (m.fragments ?? []).some((f) => f.type === 'TOOL_SEARCH')) {
      sample = m
    }
  }
}

console.log(`messages in archive: ${messageTotal}`)
console.log(`fragment types: ${[...fragmentTypes].sort().join(', ')}`)

if (sample) {
  const search = sample.fragments.find((f) => f.type === 'TOOL_SEARCH')
  console.log(`TOOL_SEARCH sample: ${search.results?.length ?? 0} results, first url=${search.results?.[0]?.url}`)
}

const hasParentChain = sessionNames.some((name) => {
  const session = JSON.parse(entries.get(name).toString('utf8'))
  return (session.messages ?? []).some((m) => m.parent_id !== null)
})
console.log(`parent_id chain: ${hasParentChain ? 'YES' : 'NO'}`)

// 3) 导入回环
const before = (await (await fetch(`${GW}/api/v1/conversations`)).json()).items.length
const importRes = await fetch(`${GW}/api/v1/import`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/zip' },
  body: zip,
})
const imported = await importRes.json()
const after = (await (await fetch(`${GW}/api/v1/conversations`)).json()).items.length

console.log(`import: ${importRes.status} -> ${JSON.stringify(imported)}`)
console.log(`conversations ${before} -> ${after}`)

const ok =
  names[0] === 'manifest.json' &&
  sessionNames.length > 0 &&
  entries.has('chat_session_index.json') &&
  imported.conversations === sessionNames.length &&
  after === before + sessionNames.length

console.log(ok ? 'PASS' : 'FAIL')
process.exit(ok ? 0 : 1)
