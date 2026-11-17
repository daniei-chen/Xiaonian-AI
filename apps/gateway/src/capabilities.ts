import { resolve } from 'node:path'
import {
  CapabilityRegistry,
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
}

/**
 * 按 config/capabilities.yaml 装配 provider。
 * 只装配密钥齐备、且本轮已实现 driver 的条目——缺 key 的静默跳过，
 * 让「配了但没填密钥」不至于把整个 Gateway 拖挂。
 */
export const buildCapabilities = async (env: Env): Promise<CapabilitiesRuntime> => {
  const config = await loadCapabilitiesConfig(resolve(env.rootDir, 'config/capabilities.yaml'))
  const registry = new CapabilityRegistry()

  const chatSection = config.chat
  for (const [name, entry] of Object.entries(chatSection?.providers ?? {})) {
    // anthropic driver 尚未实现（下一轮补），这里只装 openai 协议
    if (entry.protocol !== 'openai') continue
    if (!entry.baseURL || !entry.model) continue

    const key = entry.apiKeyEnv ? apiKey(entry.apiKeyEnv) : undefined
    if (!key) continue

    registry.register(
      new OpenAiChatCapability({
        provider: name,
        baseURL: entry.baseURL,
        apiKey: key,
        defaultModel: entry.model,
        ...(entry.timeoutMs !== undefined ? { timeoutMs: entry.timeoutMs } : {}),
      }),
    )
  }

  const chatProviders = registry.providers('chat')
  const preferred = chatSection?.default ?? null
  const defaultChatProvider =
    preferred && chatProviders.includes(preferred) ? preferred : (chatProviders[0] ?? null)

  return { registry, config, chatProviders, defaultChatProvider }
}
