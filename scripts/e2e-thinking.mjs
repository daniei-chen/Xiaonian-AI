/**
 * 深度思考的多轮连续性：思考块的签名要在下一轮原样回传。
 * 用法：node --env-file=.env scripts/e2e-thinking.mjs
 */
const GW = process.env.XIAONIAN_BASE ?? 'http://127.0.0.1:8788'

const post = async (path, body) =>
  fetch(`${GW}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  })

const readSse = async (res) => {
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const counts = new Map()
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
      if (event === 'message.failed') failed = payload
    }
  }
  return { text, failed, counts }
}

const conv = await (await post('/api/v1/conversations', {})).json()

// 第一轮：开思考，走 anthropic 通道（只有它有签名）
const turn1 = await readSse(
  await post(`/api/v1/conversations/${conv.id}/messages`, {
    text: '简单说明为什么天空是蓝色的，两三句话。',
    thinking: true,
    provider: 'deepseek',
    maxTokens: 300,
  }),
)
console.log(`turn1: ${turn1.failed ? 'FAILED ' + turn1.failed.message : 'ok'}`)
console.log(`turn1 answer: ${turn1.text.replace(/\n/g, ' ').slice(0, 90)}`)

const detail1 = await (await fetch(`${GW}/api/v1/conversations/${conv.id}`)).json()
const assistant1 = detail1.messages.find((m) => m.role === 'assistant')
const reasoning = assistant1?.blocks?.find((b) => b.type === 'reasoning')
console.log(
  `reasoning block: ${reasoning ? `${reasoning.text.length}ch` : 'none'}, signature: ${reasoning?.signature ? 'YES' : 'NO'}`,
)

// 第二轮：历史里带签名的思考块要被回传
const turn2 = await readSse(
  await post(`/api/v1/conversations/${conv.id}/messages`, {
    text: '那为什么日落时是红色的？同样简短。',
    thinking: true,
    provider: 'deepseek',
    maxTokens: 300,
  }),
)
console.log(`turn2 events: ${[...turn2.counts.entries()].map(([k, v]) => `${k}×${v}`).join(' ')}`)
console.log(`turn2: ${turn2.failed ? 'FAILED ' + turn2.failed.code + ' ' + turn2.failed.message : 'ok'}`)
console.log(`turn2 answer: ${turn2.text.replace(/\n/g, ' ').slice(0, 90)}`)

const ok = Boolean(reasoning?.signature) && !turn1.failed && !turn2.failed
console.log(ok ? 'PASS' : 'FAIL')
process.exit(ok ? 0 : 1)
