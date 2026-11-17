/**
 * 端到端：创建会话 → 发消息（SSE）→ 回查落库。
 * 用法：node scripts/e2e-chat.mjs
 */
const BASE = process.env.XIAONIAN_BASE ?? 'http://127.0.0.1:8788'

const post = async (path, body) => {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  })
  if (!res.ok) throw new Error(`${path} -> ${res.status} ${await res.text()}`)
  return res
}

// 1) 建会话
const conversation = await (await post('/api/v1/conversations', {})).json()
console.log('conversation:', conversation.id)

// 2) 发消息，逐帧读 SSE
const res = await post(`/api/v1/conversations/${conversation.id}/messages`, {
  text: '用一句话说明什么是增量同步，不要展开。',
  thinking: false,
  maxTokens: 200,
})

const reader = res.body.getReader()
const decoder = new TextDecoder()
let buffer = ''
const counts = new Map()
let text = ''
let completed = null

for (;;) {
  const { done, value } = await reader.read()
  if (done) break
  buffer += decoder.decode(value, { stream: true })

  let idx
  while ((idx = buffer.indexOf('\n\n')) !== -1) {
    const frame = buffer.slice(0, idx)
    buffer = buffer.slice(idx + 2)

    const event = /^event: (.+)$/m.exec(frame)?.[1]
    const raw = /^data: (.+)$/m.exec(frame)?.[1]
    if (!event || !raw) continue

    counts.set(event, (counts.get(event) ?? 0) + 1)
    const payload = JSON.parse(raw)
    if (event === 'text.delta') text += payload.text
    if (event === 'message.completed') completed = payload
    if (event === 'message.failed') {
      console.error('FAILED:', payload)
      process.exit(1)
    }
  }
}

console.log('events:', [...counts.entries()].map(([k, v]) => `${k}×${v}`).join(' '))
console.log('text:', text.slice(0, 120))
console.log('usage:', JSON.stringify({
  input: completed?.usage?.inputTokens,
  output: completed?.usage?.outputTokens,
  thinking: completed?.usage?.thinkingTokens,
}))

// 3) 回查
const detail = await (await fetch(`${BASE}/api/v1/conversations/${conversation.id}`)).json()
console.log('persisted messages:', detail.messages.length)
for (const m of detail.messages) {
  const body = m.blocks.filter((b) => b.type === 'text').map((b) => b.text).join('')
  console.log(`  [${m.role}/${m.status}/seq=${m.seq}] ${body.slice(0, 60)}`)
}
console.log('title:', detail.conversation.title)

// undici 的连接池会让进程悬着，显式退出
process.exit(0)
