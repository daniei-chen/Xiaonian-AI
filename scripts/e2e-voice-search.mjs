/**
 * 两个闭环验证：
 *  1) TTS 生成语音 -> /api/v1/asr 识别 -> 比对文本（一条链验证两个能力）
 *  2) webSearch=true -> 走 anthropic 通道 -> 检查是否返回 citations
 *
 * 用法：node --env-file=.env scripts/e2e-voice-search.mjs
 */
const GW = process.env.XIAONIAN_BASE ?? 'http://127.0.0.1:8788'
const DS = process.env.DEEPSEEK_BASE_URL ?? 'https://api.deepseek.com'
const KEY = process.env.DEEPSEEK_API_KEY

if (!KEY) {
  console.error('DEEPSEEK_API_KEY missing')
  process.exit(1)
}

// ---------------------------------------------------------------- 1) 语音闭环

const SPOKEN = '今天下午三点提醒我开会'

const ttsRes = await fetch(`${DS}/v1/tools/tts`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${KEY}` },
  body: JSON.stringify({ text: SPOKEN, voice_id: 'stella', format: 'wav', filter_markdown: true }),
})

if (!ttsRes.ok) {
  console.error('TTS failed:', ttsRes.status, (await ttsRes.text()).slice(0, 200))
  process.exit(1)
}

const wav = Buffer.from(await ttsRes.arrayBuffer())
console.log(`tts: ${(wav.length / 1024).toFixed(1)} KB, spoke "${SPOKEN}"`)

const asrRes = await fetch(`${GW}/api/v1/asr`, {
  method: 'POST',
  headers: { 'Content-Type': 'audio/wav' },
  body: wav,
})

if (!asrRes.ok) {
  console.error('ASR failed:', asrRes.status, (await asrRes.text()).slice(0, 300))
  process.exit(1)
}

const asr = await asrRes.json()
const hit = asr.text.replace(/\s/g, '').includes(SPOKEN.slice(0, 4))
console.log(`asr: "${asr.text}" (${asr.durationMs} ms)  match=${hit ? 'YES' : 'NO'}`)

// ---------------------------------------------------------------- 2) 联网 + 引用

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
    text: 'DeepSeek API 最新发布的模型版本号是什么？给出确切名称。',
    webSearch: true,
    maxTokens: 400,
  }),
})

const reader = res.body.getReader()
const decoder = new TextDecoder()
let buffer = ''
const counts = new Map()
let citations = []
let text = ''
let failed = null
let completed = null

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
    if (event === 'message.completed') completed = payload
  }
}

console.log('search events:', [...counts.entries()].map(([k, v]) => `${k}×${v}`).join(' '))
if (failed) console.log('FAILED:', failed.code, failed.message)
console.log(`citations: ${citations.length}`)
for (const c of citations.slice(0, 4)) console.log(`  [${c.index}] ${c.title ?? '(no title)'} — ${c.url}`)
console.log('usage.searchCalls:', completed?.usage?.searchCalls)
console.log('text:', text.slice(0, 200).replace(/\n+/g, ' '))

process.exit(0)
