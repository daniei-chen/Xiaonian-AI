/**
 * 流式冒烟：真打一次 zhihui，验证 SSE 解析 + 块级状态机。
 * 用法：node --env-file=.env scripts/smoke-chat.mjs
 */
import { OpenAiChatCapability } from '../packages/adapters/dist/index.js'

const apiKey = process.env.ZHIHUI_API_KEY
if (!apiKey) {
  console.error('ZHIHUI_API_KEY missing')
  process.exit(1)
}

const cap = new OpenAiChatCapability({
  provider: 'zhihui',
  baseURL: process.env.ZHIHUI_BASE_URL ?? 'https://zhihui.kaogong.art/v1',
  apiKey,
  defaultModel: process.env.ZHIHUI_MODEL ?? 'deepseek-v4.1-flash',
})

const started = Date.now()
const blocks = []
const counts = new Map()
let usage = null
let finish = null

try {
  for await (const ev of cap.stream(
    {
      messages: [{ role: 'user', blocks: [{ type: 'text', text: '用一句话说明什么是流式输出。' }] }],
      maxTokens: 200,
      thinking: {
        enabled: process.env.SMOKE_THINKING === '1',
        effort: (process.env.SMOKE_EFFORT ?? 'high'),
      },
    },
    { userId: 'smoke-user', traceId: 'smoke-1' },
  )) {
    counts.set(ev.type, (counts.get(ev.type) ?? 0) + 1)
    if (ev.type === 'block_start') blocks[ev.index] = ev.block
    if (ev.type === 'text_delta' || ev.type === 'reasoning_delta') {
      const cur = blocks[ev.index]
      if (cur) cur.text += ev.text
    }
    if (ev.type === 'usage') usage = ev.usage
    if (ev.type === 'finish') finish = ev.finishReason
  }
} catch (err) {
  console.error('STREAM FAILED:', err?.constructor?.name, err?.code ?? '', err?.message)
  process.exit(1)
}

const ms = Date.now() - started
console.log('events:', [...counts.entries()].map(([k, v]) => `${k}×${v}`).join(' '))
console.log('finish:', finish, '| elapsed:', ms, 'ms')
console.log('blocks:', blocks.filter(Boolean).map((b) => `${b.type}(${b.text.length}ch)`).join(', '))
console.log('usage:', JSON.stringify({
  input: usage?.inputTokens,
  output: usage?.outputTokens,
  thinking: usage?.thinkingTokens,
  cacheHit: usage?.cacheHitTokens,
  tier: usage?.serviceTier,
}))
console.log('text:', blocks.filter(Boolean).map((b) => b.text).join(' / ').slice(0, 200))
