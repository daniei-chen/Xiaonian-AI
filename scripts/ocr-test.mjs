import { readFile } from 'node:fs/promises'
import { DeepSeekOcrCapability } from '../packages/adapters/dist/index.js'

const bytes = await readFile('D:/AI/小念/.out/test-file.png')
const cap = new DeepSeekOcrCapability({
  provider: 'deepseek',
  baseURL: 'https://api.deepseek.com',
  apiKey: process.env.DEEPSEEK_API_KEY,
})

try {
  const pages = await cap.ocr(
    {
      data: new Uint8Array(bytes),
      filename: 'test-file.png',
      mime: 'image/png',
      purpose: 'text_recognition',
    },
    { userId: 'u', traceId: 't', isolationKey: 'i' },
  )
  console.log('pages:', JSON.stringify(pages))
} catch (e) {
  console.log('ERROR:', e.constructor?.name, e.code ?? '', String(e.message).slice(0, 300))
}
process.exit(0)
