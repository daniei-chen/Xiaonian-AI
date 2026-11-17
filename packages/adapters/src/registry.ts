import type { CapabilityKind } from '@xiaonian/protocol'
import type {
  AnyCapability,
  AsrCapability,
  ChatCapability,
  FetchCapability,
  FilesCapability,
  OcrCapability,
  SearchCapability,
  TtsCapability,
} from './capability.js'

type ByKind = {
  chat: ChatCapability
  search: SearchCapability
  fetch: FetchCapability
  ocr: OcrCapability
  asr: AsrCapability
  tts: TtsCapability
  files: FilesCapability
}

/**
 * 编译期注册表 + 配置选择的组合。
 * 不做热插拔、不做动态加载——P0 够用，且省掉大量生命周期问题。
 */
export class CapabilityRegistry {
  readonly #byKind = new Map<CapabilityKind, Map<string, AnyCapability>>()

  register(cap: AnyCapability): this {
    let bucket = this.#byKind.get(cap.kind)
    if (!bucket) {
      bucket = new Map()
      this.#byKind.set(cap.kind, bucket)
    }
    if (bucket.has(cap.provider)) {
      throw new Error(`duplicate capability: ${cap.kind}/${cap.provider}`)
    }
    bucket.set(cap.provider, cap)
    return this
  }

  has(kind: CapabilityKind, provider: string): boolean {
    return this.#byKind.get(kind)?.has(provider) ?? false
  }

  #resolve<K extends CapabilityKind>(kind: K, provider: string): ByKind[K] {
    const found = this.#byKind.get(kind)?.get(provider)
    if (!found) throw new Error(`capability not registered: ${kind}/${provider}`)
    return found as ByKind[K]
  }

  chat(provider: string): ChatCapability {
    return this.#resolve('chat', provider)
  }
  search(provider: string): SearchCapability {
    return this.#resolve('search', provider)
  }
  fetch(provider: string): FetchCapability {
    return this.#resolve('fetch', provider)
  }
  ocr(provider: string): OcrCapability {
    return this.#resolve('ocr', provider)
  }
  asr(provider: string): AsrCapability {
    return this.#resolve('asr', provider)
  }
  tts(provider: string): TtsCapability {
    return this.#resolve('tts', provider)
  }
  files(provider: string): FilesCapability {
    return this.#resolve('files', provider)
  }

  providers(kind: CapabilityKind): string[] {
    return [...(this.#byKind.get(kind)?.keys() ?? [])]
  }

  /** 供 UI 能力探测：某类能力下每个 provider 自述的特性 */
  describe(kind: CapabilityKind): Array<{ provider: string; features: readonly string[] }> {
    const bucket = this.#byKind.get(kind)
    if (!bucket) return []
    return [...bucket.values()].map((c) => ({ provider: c.provider, features: c.caps().features }))
  }
}
