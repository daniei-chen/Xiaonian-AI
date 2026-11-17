/**
 * 抓一份 OpenAI 兼容格式的原始 SSE，作为主通道的 golden 基准。
 * 用法：node --env-file=.env scripts/capture-openai-sse.mjs
 */
import { writeFile } from 'node:fs/promises'

const apiKey = process.env.ZHIHUI_API_KEY
if (!apiKey) {
  console.error('ZHIHUI_API_KEY missing')
  process.exit(1)
}

const res = await fetch(`${process.env.ZHIHUI_BASE_URL ?? 'https://zhihui.kaogong.art/v1'}/chat/completions`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    Authorization: `Bearer ${apiKey}`,
  },
  body: JSON.stringify({
    model: process.env.ZHIHUI_MODEL ?? 'deepseek-v4.1-flash',
    messages: [{ role: 'user', content: '时区是什么？用一句话回答。' }],
    max_tokens: 200,
    reasoning_effort: 'high',
    stream: true,
  }),
})

if (!res.ok) {
  console.error('HTTP', res.status, await res.text())
  process.exit(1)
}

const raw = await res.text()
const out = 'packages/protocol/fixtures/captured/openai-chat-stream-thinking.sse.txt'
await writeFile(out, raw, 'utf8')

const frames = (raw.match(/^data: /gm) ?? []).length
const hasDone = raw.includes('data: [DONE]')
const hasUsage = raw.includes('"usage":{"prompt_tokens"') || raw.includes('"usage": {')
console.log(`wrote ${out}`)
console.log(`bytes=${raw.length} frames=${frames} done_sentinel=${hasDone} usage_in_stream=${hasUsage}`)
