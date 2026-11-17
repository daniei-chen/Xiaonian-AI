/**
 * 重新生成 / 继续生成 的语义验证。
 * 用法：node scripts/e2e-replay.mjs
 */
const GW = process.env.XIAONIAN_BASE ?? 'http://127.0.0.1:8788'

const post = async (path, body) => {
  const res = await fetch(`${GW}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  })
  return res
}

const readSseText = async (res) => {
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
  return { text, failed }
}

const detail = async (id) => (await fetch(`${GW}/api/v1/conversations/${id}`)).json()

// 1) 首轮
const conv = await (await post('/api/v1/conversations', {})).json()
const first = await readSseText(
  await post(`/api/v1/conversations/${conv.id}/messages`, {
    text: '只回答一个单词：ALPHA',
    maxTokens: 50,
  }),
)
console.log(`turn1: "${first.text.trim()}"`)

let d = await detail(conv.id)
console.log(`messages after turn1: ${d.messages.length}`)
const userMessage = d.messages.find((m) => m.role === 'user')
const oldAssistant = d.messages.find((m) => m.role === 'assistant')

// 2) 从那条用户消息重放（等同"重新生成"）
const second = await readSseText(
  await post(`/api/v1/conversations/${conv.id}/messages`, {
    text: userMessage.blocks.map((b) => b.text ?? '').join(''),
    maxTokens: 50,
    replayFromMessageId: userMessage.id,
  }),
)
console.log(`regenerated: "${second.text.trim()}"`)

d = await detail(conv.id)
const newAssistant = d.messages.find((m) => m.role === 'assistant')
console.log(`messages after replay: ${d.messages.length} (expect 2)`)
console.log(`old assistant removed: ${d.messages.every((m) => m.id !== oldAssistant.id) ? 'YES' : 'NO'}`)
console.log(`new assistant created: ${newAssistant && newAssistant.id !== oldAssistant.id ? 'YES' : 'NO'}`)

// 3) 继续生成
const third = await readSseText(
  await post(`/api/v1/conversations/${conv.id}/messages`, {
    text: '',
    maxTokens: 80,
    continueGeneration: true,
  }),
)
console.log(`continued: "${third.text.trim().slice(0, 80)}"`)
d = await detail(conv.id)
console.log(`messages after continue: ${d.messages.length} (expect 4)`)

const ok =
  d.messages.length === 4 &&
  second.text.trim().length > 0 &&
  third.text.trim().length > 0
console.log(ok ? 'PASS' : 'FAIL')
process.exit(ok ? 0 : 1)
