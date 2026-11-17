/**
 * 工具能力 + 深度搜索验证。
 * 用法：node scripts/e2e-tools.mjs
 */
import { readFile } from 'node:fs/promises'

const GW = process.env.XIAONIAN_BASE ?? 'http://127.0.0.1:8788'

const post = (path, body, headers = {}) =>
  fetch(`${GW}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: typeof body === 'string' || body instanceof Uint8Array ? body : JSON.stringify(body),
  })

// 1) 搜索
const searchRes = await post('/api/v1/search', { query: 'DeepSeek V4.1 Flash 发布', count: 3 })
const search = await searchRes.json()
console.log(`search: ${searchRes.status} -> ${search.results?.length ?? 0} 条`)
for (const r of (search.results ?? []).slice(0, 2)) {
  console.log(`  ${r.title?.slice(0, 40)} | ${r.url} | snippet=${r.snippet?.length ?? 0}ch`)
}

// 2) 读链接
const fetchRes = await post('/api/v1/fetch', { url: 'https://api-docs.deepseek.com/zh-cn/' })
const page = await fetchRes.json()
console.log(`fetch: ${fetchRes.status} -> ${page.markdown?.length ?? 0} ch, title=${page.title ?? 'null'}`)

// 3) OCR：直接发原始字节，别走会 JSON.stringify 的 post 包装
const png = await readFile('D:/AI/小念/.out/test-file.png')
const ocrRes = await fetch(`${GW}/api/v1/ocr`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/octet-stream',
    'X-Mime': 'image/png',
    'X-Filename': 'test-file.png',
  },
  body: png,
})
const ocr = await ocrRes.json()
console.log(`ocr: ${ocrRes.status} -> "${(ocr.text ?? '').replace(/\n/g, ' ').slice(0, 80)}"`)

// 4) 检索：默认深度搜索；SEARCH_MODE=light 测浅搜索（也走自建 loop）
const mode = process.env.SEARCH_MODE ?? 'deep'
const conv = await (await post('/api/v1/conversations', {})).json()
const res = await post(`/api/v1/conversations/${conv.id}/messages`, {
  text: 'DeepSeek 最新发布的模型版本号是什么？',
  deepSearch: mode === 'deep',
  webSearch: mode === 'light',
  maxTokens: 400,
})

const reader = res.body.getReader()
const decoder = new TextDecoder()
let buffer = ''
const counts = new Map()
let citations = []
let text = ''
let failed = null

for (;;) {
  const { done, value } = await reader.read()
  if (done) break
  buffer += decoder.decode(value, { stream: true })
  let i
  while ((i = buffer.indexOf('\n\n')) !== -1) {
    const frame = buffer.slice(0, i)
    buffer = buffer.slice(i + 2)
    const event = /^event: (.+)$/m.exec(frame)?.[1]
    const raw = /^data: (.+)$/m.exec(frame)?.[1]
    if (!event || !raw) continue
    counts.set(event, (counts.get(event) ?? 0) + 1)
    const payload = JSON.parse(raw)
    if (event === 'text.delta') text += payload.text
    if (event === 'citations') citations = payload.items ?? []
    if (event === 'message.failed') failed = payload
  }
}

console.log(`search loop (${mode}) events:`, [...counts.entries()].map(([k, v]) => `${k}×${v}`).join(' '))
if (failed) console.log('FAILED:', failed.code, failed.message)
console.log(`citations: ${citations.length}`)
for (const c of citations.slice(0, 3)) console.log(`  [${c.index}] ${c.title?.slice(0, 40)}`)
console.log('answer:', text.replace(/\n+/g, ' ').slice(0, 180))

const refMark = /\[\d+\]/.test(text)
console.log(`answer cites sources: ${refMark ? 'YES' : 'NO'}`)

process.exit(0)
