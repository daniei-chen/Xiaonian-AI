/**
 * SSE 读帧。两种协议（OpenAI 的 data-only、Anthropic 的 event+data）共用一套解析，
 * 差异留给调用方按 frame.event 处理。
 *
 * 规范要点：
 * - 帧以空行分隔
 * - `:` 开头是注释（保活），丢弃
 * - `data:` 可多行，按 \n 拼接
 * - 结束哨兵（OpenAI 的 [DONE]）不做特殊处理，交给调用方
 */
export interface SseFrame {
  /** 仅 Anthropic 风格会带 */
  event?: string
  data: string
}

const FRAME_SEPARATOR = /\r?\n\r?\n/

const parseFrame = (raw: string): SseFrame | null => {
  let event: string | undefined
  const dataLines: string[] = []

  for (const line of raw.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // 去掉前缀后只有一个可选前导空格
      const v = line.slice(5)
      dataLines.push(v.startsWith(' ') ? v.slice(1) : v)
    }
  }

  if (dataLines.length === 0) return null
  const data = dataLines.join('\n')
  return event ? { event, data } : { data }
}

export async function* readSse(
  body: ReadableStream<Uint8Array>,
  signal?: AbortSignal,
): AsyncIterable<SseFrame> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      if (signal?.aborted) throw new DOMException('The operation was aborted.', 'AbortError')
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      for (;;) {
        const match = FRAME_SEPARATOR.exec(buffer)
        if (!match || match.index === undefined) break
        const raw = buffer.slice(0, match.index)
        buffer = buffer.slice(match.index + match[0].length)
        const frame = parseFrame(raw)
        if (frame) yield frame
      }
    }

    // 流结束时末尾可能没有空行
    const tail = buffer.trim()
    if (tail) {
      const frame = parseFrame(tail)
      if (frame) yield frame
    }
  } finally {
    reader.releaseLock()
  }
}
