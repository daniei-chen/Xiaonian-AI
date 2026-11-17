/**
 * 进程内的活动流表。停止生成走这里发 abort 信号。
 * P0 是单进程部署；将来多 worker 要把这张表挪到 PG 或 Redis。
 */
interface ActiveStream {
  ac: AbortController
  userId: string
  startedAt: number
}

const active = new Map<string, ActiveStream>()

export const registerStream = (messageId: string, userId: string, ac: AbortController): void => {
  active.set(messageId, { ac, userId, startedAt: Date.now() })
}

export const unregisterStream = (messageId: string): void => {
  active.delete(messageId)
}

/** 只有消息所属用户能中断；返回是否真的发出了 abort */
export const abortStream = (messageId: string, userId: string): boolean => {
  const entry = active.get(messageId)
  if (!entry || entry.userId !== userId) return false
  entry.ac.abort()
  return true
}

export const activeStreamCount = (): number => active.size
