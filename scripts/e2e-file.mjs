/**
 * 文件上传 + 图文问答闭环。
 * 用法：node --env-file=.env scripts/e2e-file.mjs
 */
import { readFile } from 'node:fs/promises'

const GW = process.env.XIAONIAN_BASE ?? 'http://127.0.0.1:8788'
const PATH = 'D:/AI/小念/.out/test-file.png'

const bytes = await readFile(PATH)
console.log(`local file: ${(bytes.length / 1024).toFixed(1)} KB`)

// 1) 上传（原始字节 + X-Mime / X-Filename）
const up = await fetch(`${GW}/api/v1/files`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/octet-stream',
    'X-Mime': 'image/png',
    'X-Filename': encodeURIComponent('test-file.png'),
  },
  body: bytes,
})

if (!up.ok) {
  console.error('UPLOAD FAILED:', up.status, (await up.text()).slice(0, 300))
  process.exit(1)
}

const file = await up.json()
console.log('uploaded:', JSON.stringify(file))

// 2) 带文件发消息
const conv = await (
  await fetch(`${GW}/api/v1/conversations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  })
).json()

const res = await fetch(`${GW}/api/v1/conversations/${conv.id}/messages`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    text: '这张图片里写的 secret word 是什么？只回答那个词。',
    maxTokens: 200,
    files: [{ fileId: file.fileId, kind: 'image', name: file.name, mime: file.mime }],
  }),
})

const reader = res.body.getReader()
const decoder = new TextDecoder()
let buffer = ''
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
    const payload = JSON.parse(raw)
    if (event === 'text.delta') text += payload.text
    if (event === 'message.failed') failed = payload
  }
}

if (failed) {
  console.log('MESSAGE FAILED:', failed.code, failed.message)
  process.exit(1)
}

const hit = /pangolin/i.test(text)
console.log(`answer: ${text.trim().slice(0, 160)}`)
console.log(`contains PANGOLIN: ${hit ? 'YES' : 'NO'}`)
process.exit(hit ? 0 : 2)
