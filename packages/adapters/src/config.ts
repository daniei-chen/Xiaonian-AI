import { readFile } from 'node:fs/promises'
import { parse as parseYaml } from 'yaml'
import { z } from 'zod'

/** 单个 provider 的声明。只有 kind 是代码，其余全是数据。 */
export const ProviderEntrySchema = z.object({
  /** 适配器标识，如 deepseek-search / anysearch-mcp */
  kind: z.string().optional(),
  /** chat 专用：用哪套协议 */
  protocol: z.enum(['openai', 'anthropic']).optional(),
  baseURL: z.string().url().optional(),
  /** 密钥从环境变量取，绝不写进配置文件 */
  apiKeyEnv: z.string().optional(),
  model: z.string().optional(),
  timeoutMs: z.number().int().positive().optional(),
})
export type ProviderEntry = z.infer<typeof ProviderEntrySchema>

export const CapabilitySectionSchema = z.object({
  default: z.string().min(1),
  /** 主 provider 失败时按顺序降级 */
  fallback: z.array(z.string()).default([]),
  providers: z.record(z.string(), ProviderEntrySchema),
})
export type CapabilitySection = z.infer<typeof CapabilitySectionSchema>

export const CapabilitiesConfigSchema = z.record(z.string(), CapabilitySectionSchema)
export type CapabilitiesConfig = z.infer<typeof CapabilitiesConfigSchema>

export const loadCapabilitiesConfig = async (path: string): Promise<CapabilitiesConfig> => {
  const raw = await readFile(path, 'utf8')
  const parsed: unknown = parseYaml(raw)
  return CapabilitiesConfigSchema.parse(parsed ?? {})
}

/**
 * 解析某类能力的调用链：[default, ...fallback]。
 * 调用方按顺序尝试，第一个成功即返回。
 */
export const resolveChain = (
  config: CapabilitiesConfig,
  kind: string,
): string[] => {
  const section = config[kind]
  if (!section) return []
  return [section.default, ...section.fallback].filter(
    (name, i, all) => all.indexOf(name) === i && name in section.providers,
  )
}

export const providerEntry = (
  config: CapabilitiesConfig,
  kind: string,
  provider: string,
): ProviderEntry | undefined => config[kind]?.providers[provider]
