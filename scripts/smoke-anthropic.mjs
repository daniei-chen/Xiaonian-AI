/**
 * anthropic 通道冒烟：真打 DeepSeek /anthropic/v1/messages。
 * 用法：node --env-file=.env scripts/smoke-anthropic.mjs
 */
import { AnthropicChatCapability } from '../packages/adapters/dist/index.js'

const apiKey = process.env.DEEPSEEK_API_KEY
if (!apiKey) {
  console.error('DEEPSEEK_API_KEY missing')
  process.exit(1)
}

const cap = new AnthropicChatCapability({
  provider: 'deepseek',
  baseURL: process.env.DEEPSEEK_BASE_URL ?? 'https://api.deepseek.com',
  apiKey,
  defaultModel: 'deepseek-flash',
})

const useTools = process.env.SMOKE_TOOLS === '1'
const prompt = process.env.SMOKE_PROMPT ?? '用一句话说明什么是时区。'
const started = Date.now()
const blocks = []
const counts = new Map()
let usage = null
let finish = null

try {
  for await (const ev of cap.stream(
    {
      messages: [
        { role: 'user', blocks: [{ type: 'text', text: prompt }] },
      ],
      maxTokens: 200,
      thinking: { enabled: false },
      ...(useTools ? { tools: ['deepseek_web_search'] } : {}),
    },
    { userId: 'smoke', traceId: 't-anthropic', isolationKey: 'smoke' },
  )) {
    counts.set(ev.type, (counts.get(ev.type) ?? 0) + 1)
    if (ev.type === 'block_start') blocks[ev.index] = { ...ev.block }
    if (ev.type === 'text_delta' || ev.type === 'reasoning_delta') {
      const b = blocks[ev.index]
      if (b) b.text += ev.text
    }
    if (ev.type === 'usage') usage = ev.usage
    if (ev.type === 'finish') finish = ev.finishReason
  }
} catch (err) {
  console.error('FAILED:', err?.constructor?.name, err?.code ?? '', err?.message)
  process.exit(1)
}

console.log('events:', [...counts.entries()].map(([k, v]) => `${k}×${v}`).join(' '))
console.log('finish:', finish, '| elapsed:', Date.now() - started, 'ms')
console.log('blocks:', blocks.filter(Boolean).map((b) => `${b.type}(${b.text.length}ch)`).join(', '))
console.log('usage:', JSON.stringify({
  input: usage?.inputTokens,
  output: usage?.outputTokens,
  thinking: usage?.thinkingTokens,
  search: usage?.searchCalls,
}))
console.log('text:', blocks.filter(Boolean).map((b) => b.text).join(' / ').slice(0, 160))
process.exit(0)
