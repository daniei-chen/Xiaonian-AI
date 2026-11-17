# 小念 · 第一轮（阶段一）实施计划

> 输入：`xiaonian_v0_2_development_spec.md` v0.2、`xiaonian_p0_development_checklist.md`、`deepseek-ai/chat-demo`（MIT，4161★，2026-11-13 更新）
> 本轮范围：**阶段一 · DeepSeek 全功能替代**。Agent Runtime（任务账本 / 提醒闭环 / 审批）不在本轮交付，但要为它留好数据与事件边界。
> 事实核实：2026-11-17，`api-docs.deepseek.com` + chat-demo 源码实读。本轮未写业务代码。

---

## 1. 第一轮交付什么

用户在小念里能完成 DeepSeek App 的主要工作，共 12 项：

| # | 能力 | 依赖 |
|---|---|---|
| 1 | 聊天（多会话、自动标题、重命名、删除、搜索） | 2 |
| 2 | 流式回复 + 停止 + 继续生成 + 重新生成 + 编辑 | 3 |
| 3 | 深度思考（`thinking` + effort 三档） | — |
| 4 | 搜索（含图片搜索）+ 引用展示 | 3 |
| 5 | 深度搜索（思考中自主搜索/开页/页内查找） | 3 |
| 6 | 读链接（`deepseek_read_url`） | 3 |
| 7 | 文件问答（PDF / Word / PPT / Excel / 文本 / 图片） | 3 |
| 8 | OCR | 7 |
| 9 | ASR 语音输入 | — |
| 10 | TTS 朗读 | — |
| 11 | 历史导入 / 导出 | 7 |
| 12 | 移动端使用（Android 适配、通知深链预留、断线恢复） | 1 |

**Exit Criteria**：spec §16.1 + §16.2 的全部勾选项，加上 §16.4 里可度量的那几条（见第 13 节修正后的口径）。

**明确移出本轮**：M7 任务账本、M8 提醒工具、M9 审批与安全策略执行。它们进第二轮。但本轮的数据模型必须预留 `task_id` 外键位与 `AuditEvent` 表，否则第二轮要改表。

---

## 2. 已核实的 API 契约

三套对话协议 + 五套工具 API，**官方参考实现（chat-demo）实际只用 Messages（Anthropic 兼容）一套**。这是本轮最重要的技术事实。

### 2.1 Messages API（本轮主通道）

```http
POST https://api.deepseek.com/anthropic/v1/messages
x-api-key: <key>                                   # 也可 Authorization: Bearer
anthropic-beta: files-api-2025-04-14               # 仅含 file_id 引用时必传
Content-Type: application/json
Accept: text/event-stream
```

请求体（chat-demo 实测形态）：

```jsonc
{
  "model": "deepseek-flash",                       // 或 deepseek-v4-pro（不支持图像理解）
  "system": "...",
  "messages": [ /* 见 2.2 */ ],
  "thinking": { "type": "enabled" },               // enabled | disabled
  "reasoning_effort": "high",                      // low | high | max（OpenAI 格式叫法）
  "tools": [
    { "type": "deepseek_web_search", "name": "deepseek_web_search" },
    { "type": "deepseek_read_url",   "name": "deepseek_read_url"  }
  ],
  "safety_check": { "level": "high" },
  "title": { "enabled": true },                    // ★ 服务端直接生成会话标题
  "stream": true
}
```

**`title` 参数是白捡的**：spec §6.1 要「会话自动标题」，不用自己另调一次模型——`message_delta` 事件里会带回 `title` 字段。

### 2.2 消息角色（四种，不是三种）

| role | content | 说明 |
|---|---|---|
| `latest_reminder` | `string` | **Chat Engine 扩展角色**，必须紧挨在对应的 `user` 之前 |
| `user` | `string` 或 `(text \| image \| document)[]` | 带文件时用块数组 |
| `assistant` | `(text \| thinking \| server_tool_use \| web_search_tool_result \| web_fetch_tool_result)[]` | 业务是否回传有严格规则，见下方 |
| （末尾 assistant） | — | **末尾放 assistant 即自动继续生成，不需要 `prefix` 字段** |

历史回传规则（chat-demo 的取舍，直接影响上下文正确性）：

- 回传：正文 `text`、`read_link` 的 `web_fetch_tool_result`（含 `signature`，服务端靠它解密恢复网页）与对应 `server_tool_use`；
- **不回传**：`thinking` 块、`web_search_tool_result`、`deep_search` 的 fetch 结果、`image_search` 结果；
- 不回传的块只用于本地展示。

### 2.3 `latest_reminder` 的格式（spec 完全没定义）

模型靠它知道「今天几号、星期几、用户在哪」。chat-demo 的拼法：

```
2026-11-17,星期二,广东,Web,中文
```

= `日期,星期,地区名,平台,语言`，逗号连接，**日期与星期必须用用户本地时区算**（不是 UTC，否则跨日会错一天）。

小念的取值：平台填 `Android`；地区由用户设置或 `X-Timezone` 推断到省级；语言取 `locale`。

> 这是「明天六点」能被正确理解的**前提条件之一**，但**不是充分条件**——精确时间仍必须结构化落库，不能让模型自由发挥。

### 2.4 流式事件（SSE）

以 `message_stop` 结束，**没有 `[DONE]`**。事件名与 JSON 的 `type` 同名。

| 事件 | 关键字段 |
|---|---|
| `message_start` | `message.id`（= 响应头 `x-ds-request-id`，不带 `msg_` 前缀的 UUID）、`usage` |
| `content_block_start` | `index`、`content_block`（**工具块在此已是完整内容**） |
| `content_block_delta` | `text_delta` / `thinking_delta` / `signature_delta` / `citations_delta` / `input_json_delta` |
| `content_block_stop` | `index` |
| `message_delta` | `stop_reason`（`end_turn`/`max_tokens`/`tool_use`/`refusal`）、`title?`、`stop_details?`（仅 refusal）、**累计 `usage`** |
| `message_stop` | — |
| `ping` | 忽略 |

`message_start` 里 `output_tokens` 恒为 0，真正的用量在 `message_delta`。

### 2.5 服务端工具块（搜索/读链接）

`server_tool_use` 的 `id` 形如 `srvtoolu_{s|ds|rl|is|dis}_{序号}_{32位hex}`，`kind` 区分五种能力：

| kind | 触发 | input.action |
|---|---|---|
| `search` | 非思考 + web_search | `search`（`queries[]`） |
| `deep_search` | 思考 + web_search | `search` / `open_page` / `find_in_page` |
| `read_link` | 声明 read_url | `read_link`（`url`） |
| `image_search` | 非思考 + 图搜意图 | `search` |
| `deep_image_search` | 思考 + 图搜意图 | `search` |

结果块：

- `web_search_tool_result` → `content[]`：`web_search_result`（`title`/`url`/`snippet`/`site_icon`/`site_name`/`page_age`/`image_url?`/`encrypted_content`）或 `web_search_tool_result_error`（`error_code: "unavailable"`）
- `web_fetch_tool_result` → `web_fetch_result`（`content.document.source.data` 为 text/plain）或 `web_fetch_tool_result_error`（`url_not_accessible` / `url_not_allowed` / `unavailable`）

**两个硬约束**：

1. `encrypted_content` 是 AES-256-GCM 密文，**客户端解密不了**——摘要只能用 `snippet`，不要试图还原全文；
2. `read_link` 成功时返回 `signature`，**多轮请求必须原样回传**，否则服务端无法恢复网页上下文。

### 2.6 引用

`text` 块上的 `citations[]`：`{ type: "web_search_result_location", url, title|null, cited_text, encrypted_index }`。

- 一个文本块「挂过引用之后，后续正文另起新块」；同块内引用按 `url` 去重；
- `citations_delta` 在流式里逐个下发；
- UI 的角标序号由本地按出现顺序编，**不是服务端给的**。

### 2.7 Files API（与官方文档的 `/files` 不同）

```http
POST   https://api.deepseek.com/anthropic/v1/files          # multipart
DELETE https://api.deepseek.com/anthropic/v1/files/{file_id}
```

`purpose` 只有两个值（**注意与官方文档 `/files` 那套的三个值不同**）：

| purpose | 适用 |
|---|---|
| `rich_layout_recognition` | PDF / Word / PPT —— 服务端做版面解析 |
| `user_data` | 纯文本、代码、表格(Excel)、电子书、**图片（图片不传 purpose）** |

返回 `FileObject{id, type, size_bytes, created_at, filename, mime_type}`。
`purpose` 字段**必须排在 `file` 之前** append，否则服务端解析失败。

在消息里引用：`{ type: "image" | "document", source: { type: "file", file_id } }`。
**扩展模式（带 web tools / safety_check）下不支持 `document`，会返回 400** —— 即「搜索 + PDF 问答」不能同一条请求里做。

### 2.8 工具 API（独立 REST，前缀 `/v1/tools`）

| 能力 | 端点 | 约束 |
|---|---|---|
| Search | `POST /v1/tools/search` | `query`(1–128) / `count`(1–50, 默认10) / `need_content` / `ori_question`；**不支持图片搜索** |
| Fetch | `POST /v1/tools/fetch` | 返回 Markdown |
| OCR | `POST /v1/tools/ocr` | multipart，**只许 `file` + `purpose` 两个字段**，多传即 400；100 MiB / 1000 页 / 上传 120 s / 总 180 s |
| ASR | `POST /v1/tools/asr` | **非流式**，一次返回全文；16-bit PCM WAV 或 Ogg Opus；≤8 MiB；≤120 s；无 language 参数 |
| TTS | `POST /v1/tools/tts` | JSON `{text, voice_id, format:"pcm", filter_markdown:true}` → **裸 PCM 流**，24 kHz / 单声道 / 16-bit LE；≤10 000 字符；**首帧产出即返回 200** |
| TTS(WS) | `wss://api.deepseek.com/v1/tools/tts` | 边生成边念；`Sec-WebSocket-Protocol: tts, bearer.<key>` |

TTS 的 `filter_markdown` 不只去 Markdown：服务端还会跳过代码块、表格、引用标记与长公式。

### 2.9 横切：错误 / 计费 / 限流

- 错误体：`{ "error": { "message", "type?", "param?", "code?" } }`；10 个状态码：400/401/402/408/413/422/429/500/503/504
- `402` 是本机当前真实状态（见 §12 R1）
- 限流 = **账号级并发**（flash 2500 / v4-pro 500），不是 QPS；`user_id` 参数做内容安全 / KVCache / 调度三重隔离，**Gateway 必须透传**
- 计费：搜索 5 元/千次、抓取 5 元/千次、OCR 1.5 元/千页、ASR 0.78 元/小时、TTS 3 元/万字符（限时 5 折）；token 高峰/空闲差价 2 倍
- **成本结构是语音主导，不是 token 主导**：月活 1000、人均 10 次语音（1 分钟 + 200 字 TTS）粗算 ASR ≈ 130 元、TTS ≈ 300–600 元、token ≈ 1 元。配额策略要优先掐 TTS 字符数

### 2.10 实测验证（2026-11-17，真实 key 已跑通）

余额 2.99 元可正常调用。两次真实请求的 SSE 原始响应已落到 `packages/protocol/fixtures/captured/`，作为解析层与 Android DTO 的 golden 基准。

**事件结构（web_search 实测，69 帧）**

```text
message_start
content_block_start idx=0  server_tool_use  kind=search  action=search      ← 原子，input 完整
ping
content_block_stop  idx=0
content_block_start idx=1  web_search_tool_result  kind=search  results=12  ← 原子
content_block_stop  idx=1
content_block_start idx=2  text
content_block_delta idx=2  text_delta ×N
content_block_stop  idx=2
message_delta
message_stop
```

- 服务端工具块**没有 delta**：`start` 后立刻 `stop`。只有正文块走 `start → delta×N → stop`
- `server_tool_use` 实测：`id: "srvtoolu_s_00_<32hex>"`、`kind: "search"`（s=search / ds=deep_search / rl=read_link / is=image_search / dis=deep_image_search）
- **模型会自己改写并拆分 query，还会追加 `site:` 操作符**。UI 必须展示实际查询词（`input.queries`），不能回显用户原话

**修正 §2.6：`citations` 是纯流式下发的。** `content_block_start` 的 text 块**不带** `citations` 字段（实测 0 次），全部经 `citations_delta` 逐个下发（实测 6 次）。落库时要把 delta 累积回对应 block。

**修正 §2.1：`title` 必须显式开启。** 传 `title:{enabled:true}` 的那次，`message_delta.delta.title` = `时区一句话解释`；没传的那次（带 web_search），`message_delta.delta` 只有 `{"stop_reason":"end_turn","stop_sequence":null}`，**没有 title**——搜索请求要单独开标题，或复用首轮的标题。

> **解析陷阱**：`web_search_result` 自带 `title` 字段。全局正则搜 `"title"` 会抓到搜索结果标题（我第一遍就抓错了），必须精确定位到 `message_delta` 事件再取。

**usage 实测字段（spec 与 chat-demo 的类型都没写全）**

```json
"usage": { "input_tokens": 40, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 0,
           "output_tokens": 70, "service_tier": "standard",
           "output_tokens_details": { "thinking_tokens": 53 } }
```

带搜索时多出：

```json
"server_tool_use": { "web_search_requests": 1, "web_fetch_requests": 0,
                     "tool_search": 2, "tool_fetch": 0, "tool_image_search": 0 }
```

- **`output_tokens_details.thinking_tokens` 单独计量思考 token** —— 费用模型必须区分，否则成本算不准
- `service_tier` 要落库（空闲/高峰差价 2 倍的判据之一）
- 计费计数器有 **5 个**，比 `UsageSummary` 原设计多两个（`tool_fetch`、`tool_image_search`）

**thinking 块起始形态**：`{"type":"thinking","thinking":"","signature":""}`，内容全靠 `thinking_delta` 累积，`signature` 在块结束前由 `signature_delta` 下发。

### 2.11 双 provider 拓扑（2026-11-17 定案，影响全局）

第一轮不把所有能力压在一个 provider 上：

| 能力 | Provider | 端点 | 状态 |
|---|---|---|---|
| 对话 / 思考 / 工具调用循环 | **zhihui 聚合网关** | `https://zhihui.kaogong.art/v1/chat/completions`（OpenAI 兼容） | ✅ 实测 200 |
| 搜索 / 抓取 | DeepSeek 官方**独立 API** | `POST /v1/tools/search` · `/v1/tools/fetch` | 待测 |
| 文件上传 / OCR | DeepSeek 官方 | `/files` · `/v1/tools/ocr` | 待测 |
| ASR / TTS | DeepSeek 官方 | `/v1/tools/asr` · `/v1/tools/tts` | 待测 |

zhihui 是**多模型聚合**（50 个：`glm-5.3` / `kimi-k3-1` / `minimax-m2.7` / `deepseek-v4.1-flash` …），OpenAI 兼容，实测正常。usage 里带 `quota{model,percent,reset_at}` 和 `credit`，可直接做配额显示。

**三个连锁影响，必须知道：**

1. **协议不再是单定一套，改为双 driver 并存**（见 §3）。zhihui 只认 OpenAI 格式，Messages 是 DeepSeek 独有的——两个都要实现，按 provider 组合，不二选一。
2. **`deepseek_web_search` / `deepseek_read_url` 用不了了。** 它们是「对话内建工具」，只在 `api.deepseek.com` 的对话请求里由服务端执行。对话走 zhihui 时它们不存在。搜索必须改成**自建 loop**：`/v1/tools/search` 取结果 → 注入上下文 → 模型作答。§2.5 的 `server_tool_use` / `web_search_tool_result` 块结构**本轮用不上**。
3. **深度搜索的「自主多步搜索」要自己实现**。DeepSeek 原生 deep_search 是模型在思考中自主调用；现在变成 Gateway 里跑 loop（模型决定要不要搜 → 我们搜 → 喂回 → 再决定）。工作量增加，换来的是不绑死 DeepSeek。

**代价要认**：引用角标、站点图标、`page_age` 这些原本由 DeepSeek 内建工具顺带返回的字段，现在要自己从 `/v1/tools/search` 响应里取；`citations` 结构也得自己设计（`[N]` 标记 + prompt 约束 + 本地正则解析）。

**DeepSeek 余额不再是瓶颈**：思考不走 DeepSeek，2.99 元只花在搜索/OCR/语音上。§12 R1 由「阻塞」降级为「够用但要盯」。

---

## 3. 能力层：provider 可插拔，不锁死任何一家

不只是聊天模型。**搜索、抓取、OCR、ASR、TTS、文件解析全部按能力拆成可插拔 provider**，任何一家都能换、能并存、能按用户或会话切换。

包名相应改掉：`packages/deepseek-adapter` → **`packages/adapters`**（它不是 DeepSeek 专用了）。

### 3.1 七类能力，统一接口

```ts
// packages/adapters/src/capability.ts
interface Capability<TIn, TOut> {
  readonly id: string            // "deepseek.search" / "anysearch.search"
  readonly kind: CapabilityKind  // chat | search | fetch | ocr | asr | tts | files
  caps(): ProviderCaps           // 支持什么、限制什么（长度/页数/格式）
  run(input: TIn, ctx: CallCtx): Promise<TOut>
}
```

| 能力 | canonical 输出 | 可接入 |
|---|---|---|
| `chat` | `ContentBlock[]` + `UsageSummary` | zhihui(openai) · deepseek(openai) · deepseek(anthropic) · 任意 OpenAI / Anthropic 兼容端点 |
| `search` | `SearchResult[]` | deepseek `/v1/tools/search` · **anysearch** · 可加 tavily / brave / serper / searxng |
| `fetch` | `{ url, title, publishedAt, markdown }` | deepseek `/v1/tools/fetch` · anysearch extract · jina reader |
| `ocr` | `OcrPage[]` | deepseek `/v1/tools/ocr` |
| `asr` | `{ text, durationMs }` | deepseek `/v1/tools/asr` |
| `tts` | `AsyncIterable<PcmChunk>` | deepseek `/v1/tools/tts`（HTTP 或 WS） |
| `files` | `{ fileId, provider }` | deepseek `/files` |

canonical `SearchResult` 复用历史格式里的同名结构：

```ts
type SearchResult = {
  url: string; title: string; snippet: string
  publishedAt: string | null      // RFC3339
  siteName: string | null
  siteIcon?: string | null
  raw?: unknown                   // 原始响应留档
}
```

`raw` 必须有：各家返回差异大，归一化一定丢信息，留着原始体才能在出问题时排查。

**接入方式也可以是三种**：HTTP 直连（deepseek、tavily）、MCP 客户端（anysearch 这类以 MCP 暴露的）、本地库（whisper、tesseract）。`run()` 内部怎么实现不影响上层。

### 3.2 配置驱动，按能力覆盖

```yaml
# config/capabilities.yaml
chat:
  default: zhihui
  providers:
    zhihui:   { protocol: openai,    baseURL: https://zhihui.kaogong.art/v1, model: deepseek-v4.1-flash }
    deepseek: { protocol: anthropic, baseURL: https://api.deepseek.com,      model: deepseek-flash }

search:
  default: deepseek
  fallback: [anysearch]
  providers:
    deepseek:  { kind: deepseek-search, apiKeyEnv: DEEPSEEK_API_KEY }
    anysearch: { kind: anysearch-mcp,   apiKeyEnv: ANYSEARCH_API_KEY }

tts:
  default: deepseek
  fallback: [edge]
```

- `default` 决定默认走谁，会话级 / 用户级可覆盖
- `fallback` 链：主 provider 挂了自动切备用（搜索尤其需要）
- 配置里只有 `kind` 是代码，其余全是数据

### 3.3 三方差异全收敛在 provider 内

`chat` 有多套协议，`search` 各家返回结构完全不同——**这些差异全部封在各自 provider 的 `run()` 里**。上一轮定的 `openai` / `anthropic` 双 driver 依然成立，它现在是 `chat` 能力下的两个实现，而不是整个架构。

### 3.4 本轮做多少

不追求「什么都能接」，但**接口必须先立住**，否则第二个 provider 进来就要动根基：

| 能力 | 本轮实现 | 只留接口 |
|---|---|---|
| chat | zhihui(openai)、deepseek(anthropic) | openai 官方、anthropic 官方 |
| search | deepseek、**anysearch** | tavily / brave / serper |
| fetch | deepseek、anysearch | jina reader |
| ocr / asr / tts / files | deepseek | whisper / edge-tts / 本地 OCR |

**搜索故意做两家**：它验证抽象是否真的成立。只做一家，接口永远是「刚好够用」的假象，等第三个 provider 进来才发现漏。

### 3.5 别过度设计

- 不引 DI 容器、不做动态插件加载、不做热插拔——编译期注册表 + 配置选择就够
- 不定义「能力组合 DSL」——`caps()` 返回一张扁平表
- provider 之间不互相调用（search 不调 chat），编排放在 Gateway 的 loop 里

### 3.6 chat-demo 的契约仍然有用

`types.ts` / `parseEvent.ts` 那套 Messages 契约不是废稿——**它是 `chat.anthropic` provider 的参考实现**。我上一轮说它「参考价值下降」是错判。

---

## 4. chat-demo 的使用边界

你已明确它不能当后端。为免实施时被误用，把边界写死：

### 4.1 可以照搬的（规格与契约）

| 项 | 位置 |
|---|---|
| Messages API 请求/响应类型全貌 | `src/services/api/types.ts` |
| SSE 流式事件解析（宽松 + 保留未知字段） | `src/services/api/parseEvent.ts` |
| SSE 读帧（含保活注释处理） | `src/services/api/sse.ts` |
| 错误五分类：`http` / `stream` / `network` / `interrupted` / `protocol` | `src/services/api/client.ts` |
| 语音调用形态（ASR multipart、TTS PCM 流） | `src/services/api/speech.ts` |
| 历史档案 v3 格式与校验规则 | `src/services/chat/history/{format,schema}.ts` + `README.md` |
| `latest_reminder` 五元组拼法 | `src/services/chat/latestReminder.ts` |
| 上传扩展名白名单 | `src/services/uploadExts.ts`（986 行） |
| 交互结构（会话列表 / 消息树分支 / 搜索面板 / 引用卡片 / 语音按钮 / TTS 按钮） | `src/components/**` |

### 4.2 绝不能照搬的

| 反模式 | 在小念里的正确做法 |
|---|---|
| 浏览器直连 `api.deepseek.com` | 全部经 Gateway；Key 只存 Gateway 环境变量 |
| API Key 存 `localStorage` | 客户端只持短期 JWT（≤15 min）+ 设备密钥 |
| 会话存 IndexedDB / 附件存浏览器 | 服务端 PG + 对象存储；本地只有 Room 缓存 |
| 无鉴权、无多租户、无配额 | Gateway 必须做用户隔离、限流、计费 |
| `file_id` 长期有效不做管理 | `expires_after[seconds]`（1 h–30 d）要显式设置，并跟踪失效 |
| 前端直接暴露 `encrypted_content` 之外的原始响应 | 服务端归一化后再下发，避免协议细节泄漏到 UI |

### 4.3 一个由此产生的架构冲突（spec 需要改）

spec §8.4 写的是 S3 风格两段式上传：

```
POST /files/presign
POST /files/:id/complete
```

但 DeepSeek Files API **没有 presign 机制**，只有 multipart 直传，且要求文档 120 s 内传完。所以 Gateway 只能**流式转发**（边收边 pipe 到 DeepSeek，不落盘）或落临时盘再转。

**建议改契约**：

```http
POST /files            # multipart，Gateway 边收边转发到 /anthropic/v1/files
GET  /files/:id        # 本地元数据 + DeepSeek file_id
DELETE /files/:id      # 转发 DELETE /anthropic/v1/files/{id}
```

这样客户端只需一次请求，进度用 `XHR upload.onprogress` 或 `ReadableStream` 拿。

---

## 5. spec 需要修正的 11 处

| # | spec 原文 | 真实情况 | 处置 |
|---|---|---|---|
| 1 | §4.3「封装三套协议」 | 实为 3 对话协议 + 5 工具 API，前缀不统一 | adapter 拆 `dialog/` + `tools/` 两组 driver；本轮只实现 Messages driver |
| 2 | §6.3「普通搜索 / 图片搜索 / 深度搜索」 | 五种 kind：`search`/`deep_search`/`read_link`/`image_search`/`deep_image_search` | 事件模型按五种建，别用布尔开关 |
| 3 | §6.1「会话自动标题」 | 服务端支持 `title: {enabled:true}`，`message_delta` 回传 | 不自己调模型，直接用 |
| 4 | §6.2 消息类型 | 实际是 `latest_reminder` / `user` / `assistant` 三种默认角色 + 五种 content block | 角色枚举要加 `latest_reminder` |
| 5 | §6.4 文件状态机 | 漏了 `EXPIRED`（file_id 有 `expires_after`） | 加状态；上传时显式设过期 |
| 6 | §8.4 `presign` + `complete` | DeepSeek 无 presign | 改单次 multipart 转发（§4.3） |
| 7 | §6.5 ASR `partial` / `final` | 官方 ASR 非流式，一次出全文 | 见 §6.1 决策 |
| 8 | §16.4 语音首响 P50 ≤ 800 ms | 级联架构不可达 | 拆成「说完→ASR 返回」和「ASR 返回→TTS 首帧」两个指标 |
| 9 | §16.4「ASR 成功率 ≥ 98%」 | 无置信度字段，只有 `text` + `duration_ms` | 定义为 HTTP 200 且 `text` 非空的比例 |
| 10 | §8.7 错误结构 | 缺 408/413/422/500/503/504；其中 408/413 **不可重试** | 扩充错误码表，标注 `retryable` |
| 11 | §9.1 Chat 事件 | 漏了 `title` / `usage` 累计 / `citation` 增量 / `refusal` | 事件表按 §2.4 补全 |

---

## 6. 还需要你拍板的 3 件事

### 6.1 ASR 不支持流式，`partial` 怎么办

| 方案 | partial | 中文质量 | 成本 | 说明 |
|---|---|---|---|---|
| A 纯官方 ASR（录完再识别） | ❌ | 好 | 0.78 元/h | 最简，但说话时没有实时反馈 |
| B Android 本地 `SpeechRecognizer` | ✅ | 中 | 0 | 国内设备 GMS 常缺失，不可靠 |
| **C 混合（推荐）** | 伪 | 好 | 同 A | 本地出 partial 只做「正在听」的波形/文字反馈，放开后整段送官方 ASR 出最终文本；partial 不进上下文 |
| D 第三方流式 ASR | ✅ | 取决供应商 | 另计 | 多一个供应商、密钥、账单 |

> 选 C：阶段一的「ASR 语音输入」验收按「最终文本正确」判，波形反馈不算 partial。

### 6.2 音频录制格式

官方要 16-bit PCM WAV 或 Ogg Opus，≤8 MiB、≤120 s。Android `MediaRecorder` 默认出 AAC/M4A，**不能直传**。

选一：① `AudioRecord` 直接采 PCM 写 WAV（推荐，零转码、8 MiB ≈ 4 分钟 16 kHz 单声道，够用）；② 录 Opus；③ 录 AAC 后转码（多一次 CPU，不推荐）。

### 6.3 TTS 走 HTTP 流还是 WebSocket 流

| | HTTP `POST /v1/tools/tts` | `wss://.../v1/tools/tts` |
|---|---|---|
| 首响 | 全文一次性提交，首帧即开始出声 | 可边生成边送文本，**首响更早** |
| 复杂度 | 低（chat-demo 用这条） | 中（要管 WS 生命周期、分包） |
| 适用 | 朗读已完成的回复 | 边出字边念 |

建议：**P0 先 HTTP 流**（回复已完整才有朗读需求），WS 留到「边生成边念」这个体验做的时候。但客户端要按「能切换到 WS」设计播放器（输入是 PCM 帧流，不关心来源）。

---

## 7. 技术选型（较上一版仅微调）

| 层 | 选型 | 理由 |
|---|---|---|
| 包管理 | pnpm workspace 11.22 | 已装 |
| 语言 | TypeScript 5.x，全 ESM | Node 24 |
| Gateway | **Fastify 5** | SSE 插件成熟、schema 校验内置 |
| 校验 | zod 4 | 类型单一来源 |
| ORM / 迁移 | **drizzle-orm + drizzle-kit** | 生成可读 SQL、迁移可 review |
| PG 驱动 | postgres.js | 流式 + 快 |
| 队列 | PG 表 + `SKIP LOCKED` | spec §4.5 明确 P0 不引 Redis |
| 实时 | SSE（回复流）+ `@fastify/websocket`（状态事件） | 分工明确，各自选最简形态 |
| 鉴权 | JWT（`jose`，≤15 min）+ 设备 Ed25519 | 短期令牌 |
| 日志 | pino + 脱敏 serializer | 结构化 |
| **multipart 转发** | `@fastify/multipart` + `undici` 流式 pipe | 见 §4.3，不落盘 |
| Android | Kotlin 2.x + Compose，minSdk 26 / targetSdk 36 | 本机 SDK 34/35/36 齐备 |
| Android 网络 | OkHttp + kotlinx.serialization | SSE 手解可控 |
| Android 本地 | Room | 冷启动恢复 |
| Android 架构 | MVI（`StateFlow` + sealed `UiState`） | 流式状态机天然 MVI |
| 录音 | `AudioRecord` → WAV 16 kHz 单声道 | 免转码，见 §6.2 |
| 协议共享 | zod schema + **golden JSON fixtures**；Kotlin 手写 DTO 跑同一批 fixture | TS 喂不了 Kotlin，fixture 是唯一可靠契约 |

---

## 8. 仓库结构

```text
xiaonian/                        (D:\AI\小念)
├─ apps/
│  ├─ gateway/                   # Fastify：鉴权、SSE 代理、multipart 转发、计费、限流
│  └─ mobile-android/            # Compose：会话、消息、搜索、文件、语音
├─ packages/
│  ├─ protocol/                  # zod schema + 错误码 + 事件类型 + fixtures/
│  └─ adapters/                  # capability.ts + 注册表
│     ├─ chat/{openai,anthropic}/
│     ├─ search/{deepseek,anysearch}/
│     ├─ fetch/  ocr/  asr/  tts/  files/
├─ config/
│  └─ capabilities.yaml          # provider 选择与 fallback 链
├─ infra/
│  ├─ migrations/
│  └─ local-dev/                 # docker-compose.yml + 启动脚本
├─ docs/
│  └─ p0-implementation-plan.md  # 本文档
├─ .ref/chat-demo/               # 参考实现（.gitignore，不入库）
├─ .env.example
├─ pnpm-workspace.yaml
└─ package.json
```

P0 不建：`mobile-ios`、`admin-console`、`packages/{memory,voice,approval-policy,usage-metering,observability,design-tokens,conversation}`、`adapters/*`。
理由：`usage-metering` 就是 gateway 一个模块；`voice` 就是 adapter 的两个 driver；过早拆包只增构建成本。

---

## 9. 阶段一的工作包分解

按**能力**切（不是按 spec 的 Milestone 顺序，那个顺序把「历史迁移」排在语音后面，但历史迁移只依赖文件能力，可以提前）。

| WP | 内容 | 依赖 | 估时 |
|---|---|---|---|
| **WP0** | monorepo、DB、迁移、`.env.example`、CI 基础 | — | 2 d |
| **WP1** | `protocol`：全部实体 schema + 错误码 + 事件类型 + fixture 框架 | WP0 | 3 d |
| **WP2** | `adapters` 骨架：`Capability` 接口 + 注册表 + `chat.openai`（非流式） | WP1 | 3 d |
| **WP3** | `chat.anthropic` + 双协议流式状态机 + usage 归一 | WP2 | 4 d |
| **WP4** | Gateway 会话与消息 API + SSE 代理 + 停止/继续/重生成/编辑 | WP3 | 4 d |
| **WP5** | Android：会话列表 + 消息流 + 输入框 + 停止/继续 + Markdown/代码块 | WP4 | 6 d |
| **WP6** | 深度思考 UI（`reasoning_content` 与正文分离）+ effort 三档 | WP3 | 2 d |
| **WP7** | 搜索/深搜/读链接：五种 kind 的事件模型 + 引用卡片 + 工具步骤 UI | WP3 | 5 d |
| **WP8** | 文件：上传转发、`purpose` 路由、进度、绑定会话、删除、`EXPIRED` | WP4 | 4 d |
| **WP9** | OCR：独立调用 + 页级结果展示 | WP8 | 2 d |
| **WP10** | ASR：录音 WAV、上传、错误处理、额度提示 | WP4 | 3 d |
| **WP11** | TTS：PCM 流播放器、暂停/继续、打断（≤300 ms） | WP4 | 3 d |
| **WP12** | 历史导入导出 v3（§10 详述） | WP8 | 5 d |
| **WP13** | 移动端收尾：断线恢复、冷启动、通知深链预留、用量页 | WP5 | 3 d |

总计约 47 人日。单人顺序做 ≈ 10 周；WP5/WP7/WP12 可并行。

**关键路径**：WP0 → WP1 → WP2 → WP3 → WP4 → WP5。这条链上任何一天卡住，整体顺延。

### 9.1 WP0–WP1 之后立刻做的一件小事

用一次性脚本打一遍真实 API（三套协议各一次 + 一次 web_search + 一次 read_url + 一次 OCR），把响应体存成 `packages/protocol/fixtures/` 下的 golden 文件。**这个动作依赖 R1（余额）先解决。**

好处：之后所有 unit test、Android DTO 契约测试、SSE 解析测试都跑 fixture，不烧钱、不需要网络。

---

## 10. WP12 历史导入导出（spec 只说「要支持」，实际有完整格式）

DeepSeek 导出 `deepseek_data-日期.zip`，`export_format_version: 3`。

### 10.1 归档结构

```text
manifest.json                                       # 必须首个条目
user.json                                           # 可选，导入时跳过
chat_sessions/{session_id}.json                     # 每个会话一个，含完整消息树
chat_session_index.json                             # 会话索引（在会话之后、图片之前）
chat_sessions/{session_id}_files/{file_id}_{name}   # 图片原件（仅图片）
```

导入还要兼容「会话 JSON 与图片交错、索引在末尾」的顺序。

### 10.2 消息树

`id`（会话内自增）/ `parent_id`（根为 null）还原树；父 ID < 子 ID；角色交替；`current_message_id` 决定当前分支，其他节点默认选中 ID 最大的子消息。

### 10.3 13 种 fragment

`REQUEST` / `RESPONSE` / `TEMPLATE_RESPONSE` / `THINK` / `TIP` / `CONTENT_RISK` / `PARTIAL_FILE_READ` / `TOOL_SEARCH` / `TOOL_OPEN` / `TOOL_FIND` / `READ_LINK` / `FILE` / `TOOL_IMAGE_SEARCH`

状态枚举：消息 `WIP|FINISHED|CONTENT_FILTER|FAILED|INCOMPLETE|CONTEXT_LENGTH_EXCEEDED|TIMEOUT`；工具 `WIP|FINISHED|FAILED`（`TOOL_FIND` 多 `NO_RESULT`）；`TIP.style` 为 `INFO|WARNING|CAUTION`。

### 10.4 导入的 6 条硬规则

1. 版本 > 3 → `backupNewerVersion`；≠ 3 → `backupInvalidVersion`；两者都直接失败，不碰现有会话；
2. **容器与元数据严格校验，内容片段不兼容则丢弃该片段**（不 fail 整个导入）；
3. 校验索引与会话一一对应、消息树完整、ZIP CRC 正确后，**单事务替换**；失败保留原记录；
4. 状态映射：`FINISHED`→完成，`INCOMPLETE|WIP`→已停止，`FAILED|CONTEXT_LENGTH_EXCEEDED|TIMEOUT`→错误，`CONTENT_FILTER`→拒绝；
5. 错误与模板回复**保留展示但不进后续上下文**；
6. 导入的回复一律 `prefix: false`，不能继续生成。

### 10.5 ZIP 约束

单卷 ZIP32；STORE / DEFLATE；导出用 STORE；不支持 ZIP64、加密、分卷、非 UTF-8 的非 ASCII 文件名；条目名按 NFC 比较。Node 侧用 `yauzl`（流式、可控）而非 `adm-zip`（全内存）。

### 10.6 导出不含

API Key、应用设置、正文引用角标、非图片附件原件、模型名、思考签名、原工具调用 ID、结构化错误信息、用量明细、非当前分支选择、`latestReminder` 快照。

---

## 11. 关键设计决策（沿用上一版，仍有效）

### 11.1 幂等三件套

| 场景 | 机制 |
|---|---|
| 客户端重发同一条消息 | `Idempotency-Key` header → `idempotency_keys(key, user_id, route, request_hash, response_json, expires_at)`；`request_hash` 不同返 409 |
| SSE 断线重连不重复应用 | 每条 assistant message 有 `seq`；客户端记 `lastSeq` |
| 文件重复上传 | `sha256(内容)` 去重，命中复用 `file_id` |

### 11.2 流式断线：P0 不做 delta 补拉

1. 服务端内存 ring buffer 保留最近 300 个 delta（≈30 s）；
2. 5 s 内重连 → 按 `afterSeq` 重放；
3. 超时或已淘汰 → 客户端拉最终快照改写状态，**引导用户「继续生成」**；
4. 落库为 `completed` 的消息永不重放。

不做持久化 delta 的理由：写入放大 10 倍，而「继续生成」的代价低得多。

### 11.3 时区（本轮影响 `latest_reminder`）

- 客户端每请求带 `X-Timezone: Asia/Shanghai`，Gateway 校验合法 IANA 名，非法回退 `users.timezone`；
- `latest_reminder` 的日期与星期**必须按该时区算**，不能用服务器 UTC；
- 用户表存 `iana_timezone`，导出/导入都不依赖它（档案里没有时区字段）。

### 11.4 错误归一

沿用 chat-demo 的五分类，补一个 `refusal`（`stop_reason: refusal`）：

| kind | 含义 | 重试 |
|---|---|---|
| `http` | 非 2xx | 按状态码：408/413/422 不重试；429/500/503/504 退避重试 |
| `stream` | 流中错误帧 | 不自动重试，提示 |
| `network` | 未到达服务端 | 可重试 |
| `interrupted` | 流在 `message_stop` 前结束 | 给「继续生成」 |
| `protocol` | 非 SSE / 不合协议 | 不上重试（属于我们的 bug） |
| `refusal` | 内容安全拒答 | 不重试，展示拒答 |

### 11.5 文件生命周期

`LOCAL_ONLY → UPLOADING → PROCESSING → READY → EXPIRED / FAILED / DELETING → DELETED`

`EXPIRED` 是本轮新增（spec 漏的）。上传时显式带 `expires_after[seconds]`（建议 7 天 = 604800），到期前 1 天在 UI 提示「需重新上传才能继续对话」。

---

## 12. 风险登记册

| ID | 风险 | 概率 | 影响 | 应对 |
|---|---|---|---|---|
| **R1** | DeepSeek 余额仅 2.99 元，而 OCR/ASR/TTS 都按量计费 | 已缓解 | 思考改走 zhihui 后压力大减，但语音压测会烧完 | 工具侧先打 fixture 后即停；限流 + 余额告警；生产环境必须换正式账号 |
| R2 | 扩展模式下不支持 `document`，「搜索 + PDF 问答」同轮 400 | 确定 | 产品交互要设计降级 | 明确规则：一次请求只带一种扩展能力；UI 上搜索与文件互斥提示 |
| R3 | ASR 非流式，`partial` 做不到 | 确定 | 阶段一验收口径要改 | 见 §6.1，建议方案 C |
| R4 | `file_id` 过期导致老会话里的文件问答失效 | 高 | 用户困惑 | `EXPIRED` 状态 + 提前提示 + 一键重传 |
| R5 | `encrypted_content` 不可解密，搜索结果只能靠 `snippet` | 确定 | 摘要质量受限 | UI 不要承诺全文预览；点开走 `deepseek_read_url` |
| R6 | `read_link` 的 `signature` 漏回传 → 后续轮次上下文丢失 | 中 | 多轮读链接答非所问 | `signature` 存消息字段；上下文组装测试覆盖 |
| R7 | Android 录音格式与官方要求不匹配 | 中 | ASR 全不可用 | WP10 第一件事就是 `AudioRecord` 出 WAV 单测 |
| R8 | 本机无 pgvector，向量能力无法验证 | 中 | 记忆（P1）延后 | 阶段一不依赖 pgvector |
| R9 | 模拟器性能不足以判语音/流式观感 | 低 | 体验验收不准 | 需一台真机 |
| R10 | 官方 API 于 2026-11-13 刚大改，细节仍可能变 | 中 | 契约漂移 | fixture 做回归；adapter 宽松解析 + 保留未知字段（chat-demo 的做法） |

---

## 13. 验收口径（修正后）

| 项 | 判定 |
|---|---|
| 普通聊天 | 连续 20 条无失败；中断后重进不重复 |
| 深度思考 | `thinking_delta` 有内容且与正文分离展示；effort 三档可切 |
| 搜索 + 引用 | 引用角标可点，URL 与服务端 `citations[].url` 一致；五种 kind 的步骤 UI 正确 |
| 深度搜索 | 出现 `open_page` / `find_in_page` 步骤且顺序合理 |
| 读链接 | 给一个公网 URL，回答含该页独有信息；**第二轮追问仍能引用该页**（验 signature 回传） |
| 文件问答 | PDF 走 `rich_layout_recognition`、图片走 vision；`EXPIRED` 后有提示 |
| OCR | 页级结果与页数一致；1000 页以内不超时 |
| ASR | 12 秒中文语音转录正确；超出 120 s 给出明确错误 |
| TTS | PCM 边收边播；暂停/继续；**打断到停止出声 ≤ 300 ms** |
| 历史导入 | 同一 ZIP 导两次不产生重复；版本 4 的 ZIP 被拒且不影响现有会话 |
| 历史导出 | 导出再导入，消息树与当前分支一致 |
| 移动端 | 冷启动恢复；飞行模式 30 s 切回后可继续 |
| 语音时延（修正） | 「用户停止说话 → ASR 返回文本」P50 ≤ 1.5 s；「ASR 返回 → TTS 首帧播放」P50 ≤ 800 ms |
| Key 安全 | 反编译 APK 搜不到 `sk-`；Key 只在 Gateway |

---

## 14. 环境

### 14.1 已就绪

Node 24.19 / pnpm 11.22 / Git 2.55 / JDK 17 / Android SDK（platforms 34-36、build-tools 34-36、emulator、system-images/android-35）/ **`emulator-5554` 已在线**。

缺：git 全局 `user.name` / `user.email` 未配（WP0 需要）。

### 14.2 需要你执行的（本会话无管理员权限）

```powershell
# 管理员 PowerShell
wsl --install
winget install --id Docker.DockerDesktop -e --accept-package-agreements --accept-source-agreements
# 重启后
docker run -d --name xiaonian-pg -e POSTGRES_USER=xiaonian -e POSTGRES_PASSWORD=xiaonian_dev `
  -e POSTGRES_DB=xiaonian -p 5432:5432 pgvector/pgvector:pg17
```

在你装好之前，开发/CI 用 **PGlite**（`@electric-sql/pglite` + `pglite-pgvector`，WASM 版 PG 跑在 Node 里，零安装）兜底。阶段一不依赖 pgvector，PGlite 足够。

---

## 15. 第一周（WP0 + WP1 + WP2 起步）

| 日 | 交付 | 验收 |
|---|---|---|
| D1 | monorepo、pnpm workspace、`.env.example`、PGlite 启动、迁移命令 | `pnpm -r build` 通过；`pnpm db:migrate` 可回滚 |
| D2 | `protocol`：`User`/`DeviceSession`/`Conversation`/`Message`/`ConversationFile`/`UsageEvent`/`AuditEvent` + 错误码表 | schema 能 parse fixture |
| D3 | `protocol`：五种 tool kind 的事件类型 + `ContentBlock` union + fixture 框架 | fixture 目录结构定型 |
| D4 | `deepseek-adapter` 骨架 + Messages driver 非流式 + 错误五分类 | 用造出来的 fixture 跑通 parse |
| D5 | Messages driver 流式：`content_block_*` 状态机 + usage 累计 + `title` 提取 | 用 fixture 回放出完整一条回复 |

**第 5 天末的可演示状态**：喂一段录好的 SSE 响应，adapter 输出结构化的 `ContentBlock[]` + `UsageSummary` + 标题。不需要真 Key。

---

## 16. 下一步

**已就绪**：zhihui key + DeepSeek key 都已写入 `.env`（已 gitignore），zhihui 对话实测通过，两份 DeepSeek SSE fixture 已落盘。

**只差三件事就能开 WP0：**

1. **§6 三个决策**（ASR 方案 / 录音格式 / TTS 走 HTTP 还是 WS）——建议 C / `AudioRecord` WAV / 先 HTTP
2. **git `user.name` / `user.email`**
3. **确认 §5 的 11 处 spec 修正**，尤其第 6 条（上传契约从 presign 改单次 multipart 转发）

Docker 你有空装；我按 PGlite 起步，装好再切。
