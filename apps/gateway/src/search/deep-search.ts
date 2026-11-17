import type { CallCtx, CapabilityRegistry } from '@xiaonian/adapters'
import type { Citation } from '@xiaonian/protocol'

export interface DeepSearchOutcome {
  /** 注入 system prompt 的检索上下文，带 [n] 编号 */
  context: string
  citations: Citation[]
  searchCalls: number
  fetchCalls: number
  /** 给前端做进度提示用 */
  resultCount: number
}

export interface DeepSearchOptions {
  maxResults?: number
  /** 真正抓正文的条数，多了又慢又贵 */
  maxFetch?: number
  maxPageChars?: number
  /** 显式指定 provider；不传就用 registry 的默认 */
  searchProvider: string
  fetchProvider: string | null
}

/**
 * 深度搜索：搜 → 抓正文 → 编号注入。
 *
 * 和 `webSearch` 的区别：那条路是把 tools 透传给上游、由它服务端执行，
 * 我们看不到中间过程、也换不了搜索源。这条是我们的 loop——用能力层
 * 的 search + fetch，任何 provider 都能替换，且来源由我们自己编号，
 * 引用才可信。
 */
export const runDeepSearch = async (
  registry: CapabilityRegistry,
  query: string,
  ctx: CallCtx,
  logger: { warn: (obj: unknown, msg: string) => void },
  options: DeepSearchOptions,
): Promise<DeepSearchOutcome> => {
  const maxResults = options.maxResults ?? 8
  const maxFetch = options.maxFetch ?? 3
  const maxPageChars = options.maxPageChars ?? 3000

  const search = registry.search(options.searchProvider)
  const results = await search.search({ query, count: maxResults }, ctx)

  const citations: Citation[] = []
  const chunks: string[] = []
  let fetchCalls = 0

  const fetchCap = options.fetchProvider ? registry.fetch(options.fetchProvider) : null
  const toFetch = fetchCap ? results.slice(0, maxFetch) : []

  for (let i = 0; i < toFetch.length; i++) {
    const item = toFetch[i]!
    const index = i + 1

    let body = item.snippet
    if (fetchCap) {
      try {
        const page = await fetchCap.fetch(item.url, ctx)
        fetchCalls++
        if (page.markdown.trim().length > 0) body = page.markdown.slice(0, maxPageChars)
      } catch (err) {
        // 抓取失败不等于这条没用，退回摘要继续
        logger.warn({ err, url: item.url }, 'deep search: fetch failed, falling back to snippet')
      }
    }

    chunks.push(`### [${index}] ${item.title}\nURL: ${item.url}\n\n${body}`)
    citations.push({ index, url: item.url, title: item.title })
  }

  // 剩余结果只给摘要：既保证覆盖面，又不把上下文撑爆
  for (let i = maxFetch; i < results.length; i++) {
    const item = results[i]!
    const index = i + 1
    chunks.push(`### [${index}] ${item.title}\nURL: ${item.url}\n\n${item.snippet}`)
    citations.push({ index, url: item.url, title: item.title })
  }

  return {
    context: chunks.join('\n\n---\n\n'),
    citations,
    searchCalls: 1,
    fetchCalls,
    resultCount: results.length,
  }
}

/** 把检索上下文包成 system 指令，重点是强制编号引用 */
export const deepSearchSystemPrompt = (context: string, today: string): string =>
  [
    `今天是 ${today}。下面是刚刚联网检索到的资料，按 [编号] 排列。`,
    '',
    '回答要求：',
    '1. 只依据这些资料回答，资料里没有的信息不要编造。',
    '2. 每条引用到资料的地方，用 [编号] 标注，例如「…取决于版本 [2]」。',
    '3. 如果资料之间冲突，指出冲突而不是自行选择一个。',
    '4. 如果资料不足以回答，直接说明缺什么。',
    '',
    '=== 检索资料 ===',
    context,
  ].join('\n')
