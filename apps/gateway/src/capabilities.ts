import { resolve } from 'node:path'
import {
  AnthropicChatCapability,
  CapabilityRegistry,
  DeepSeekFetchCapability,
  DeepSeekOcrCapability,
  DeepSeekSearchCapability,
  loadCapabilitiesConfig,
  OpenAiChatCapability,
  type CapabilitiesConfig,
} from '@xiaonian/adapters'
import { apiKey, type Env } from './env.js'

export interface CapabilitiesRuntime {
  registry: CapabilityRegistry
  config: CapabilitiesConfig
  /** 本轮对话实际可用的 provider（装配成功的那些） */
  chatProviders: string[]
  defaultChatProvider: string | null
  /** 工具类能力的默认 provider，null 表示该能力没装配成功 */
  defaults: {
    search: string | null
    fetch: string | null
    ocr: string | null
  }
}

/**
 * 按 config/capabilities.yaml 装配 provider。
 *
 * 两套协议都能装：protocol=openai 走 /chat/completions，protocol=anthropic 走
 * /anthropic/v1/messages（只有这条通道支持服务端联网工具）。
 * 缺密钥的条目静默跳过，不让"配了但没填 key"把整个 Gateway 拖挂。
 */
export const buildCapabilities = async (env: Env): Promise<CapabilitiesRuntime> => {
  const config = await loadCapabilitiesConfig(resolve(env.rootDir, 'config/capabilities.yaml'))
  const registry = new CapabilityRegistry()

  const chatSection = config.chat
  for (const [name, entry] of Object.entries(chatSection?.providers ?? {})) {
    if (!entry.baseURL || !entry.model) continue

    const key = entry.apiKeyEnv ? apiKey(entry.apiKeyEnv) : undefined
    if (!key) continue

    const common = {
      provider: name,
      baseURL: entry.baseURL,
      apiKey: key,
      defaultModel: entry.model,
      ...(entry.timeoutMs !== undefined ? { timeoutMs: entry.timeoutMs } : {}),
    }

    registry.register(
      entry.protocol === 'anthropic'
        ? new AnthropicChatCapability(common)
        : new OpenAiChatCapability(common),
    )
  }

  const chatProviders = registry.providers('chat')
  const preferred = chatSection?.default ?? null
  const defaultChatProvider =
    preferred && chatProviders.includes(preferred) ? preferred : (chatProviders[0] ?? null)

  // 工具类能力：search / fetch / ocr 都走 DeepSeek 的独立工具接口。
  // 装配进同一个 registry，这样换 anysearch 之类的 provider 只需要改配置。
  for (const kind of ['search', 'fetch', 'ocr'] as const) {
    const section = config[kind]
    for (const [name, entry] of Object.entries(section?.providers ?? {})) {
      if (!entry.baseURL) continue
      const key = entry.apiKeyEnv ? apiKey(entry.apiKeyEnv) : undefined
      if (!key) continue

      const opts = {
        provider: name,
        baseURL: entry.baseURL,
        apiKey: key,
        ...(entry.timeoutMs !== undefined ? { timeoutMs: entry.timeoutMs } : {}),
      }

      if (kind === 'search') registry.register(new DeepSeekSearchCapability(opts))
      else if (kind === 'fetch') registry.register(new DeepSeekFetchCapability(opts))
      else registry.register(new DeepSeekOcrCapability(opts))
    }
  }

  const pickDefault = (kind: 'search' | 'fetch' | 'ocr'): string | null => {
    const list = registry.providers(kind)
    const pref = config[kind]?.default ?? null
    return pref && list.includes(pref) ? pref : (list[0] ?? null)
  }

  return {
    registry,
    config,
    chatProviders,
    defaultChatProvider,
    defaults: {
      search: pickDefault('search'),
      fetch: pickDefault('fetch'),
      ocr: pickDefault('ocr'),
    },
  }
}
