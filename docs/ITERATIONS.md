# 迭代记录

每一轮做了什么、为什么这么做、以及当时没想清楚的地方。
放在仓库里是为了下次迭代能接上，而不是靠记忆。

---

## R1 · 骨架

**目标**：能跑的 monorepo，不写业务。

pnpm workspace + TS 全 ESM + Fastify + PGlite。选 PGlite 而不是真 Postgres，
是因为开发期不想让你装数据库；它就是一个 WASM 版 Postgres，SQL 完全兼容。

**当时的判断**：先把 `change_log.seq` 这个全局同步游标设计进去。后面证明是对的
——历史导入、多端同步都要用。

**没想清楚**：以为 Agent 闭环是重点。你后来否掉了，说先对齐 DeepSeek 的日常功能。

---

## R2 · 能力层

**目标**：不让代码写死在某一家模型上。

7 种能力（chat/search/fetch/ocr/asr/tts/files），每种一个接口 + 可配置 provider，
`config/capabilities.yaml` 里选默认和 fallback。

chat 有两条协议：`openai`（`/chat/completions`）和 `anthropic`
（`/anthropic/v1/messages`）。差异不只是路径：
- 只有 anthropic 有 server-side tools、思考签名、Files API 引用
- SSE 结尾一个是 `[DONE]`，一个是 `message_stop`

**关键认知**：能力层做出来了，但当时只有 chat 真的接了 provider。其余 6 种是
"配置里有、代码里空"。这个坑埋了很久，直到 R6 才填。

---

## R3 · 阶段一 12 项

按你给的表做：聊天 / 深度思考 / 搜索 / 深度搜索 / 读链接 / 文件问答 / OCR /
ASR / TTS / 引用 / 历史导入 / 移动端。

**卡住的地方**：
- Fastify 的 logger 泛型对不上，每个路由都得开 `FastifyInstance<any,...>`
- `pnpm -r build` 报 `AbortSignal` 找不到，要在每个包里加 `@types/node`
- 8787 端口被别的项目占了，换 8788

---

## R4 · 补齐搜索/OCR/读链接

之前"搜索"其实只是把 `tools: ['deepseek_web_search']` 透传给上游，由它服务端执行。
你说这不算"我们的实现"，是对的。

实测出真实端点：

```
POST /v1/tools/search  {query, count}   -> results[{title,url,snippet,summary,...}]
POST /v1/tools/fetch   {url}            -> {content: markdown, title}
POST /v1/tools/ocr     multipart        -> pages[{index,text}]
```

注意 `read_url` 是 404 —— 那是**对话里的服务端工具名**，不是独立端点。读链接 = fetch。

OCR 的 `purpose` 不是精度档位：
- `text_recognition` 出纯文本
- `rich_layout_recognition` 出带 `<|ref|><|box|>[[x1,y1,x2,y2]]` 的版面标记
  （适合表格/公式/多栏）

**深搜 loop**：搜 8 条 → 抓前 3 篇正文 → 编号注入 system prompt → 要求用 `[n]` 标注。
抓取失败的条目退回摘要，不整条丢掉。

**为什么不用 server-tools**：那条路我们看不到中间过程、换不了搜索源、来源也没法自己编号。

---

## R5 · 思考签名

第一版不回传思考块，理由是"各 provider 语义不一致"。这个理由站不住——
参考实现里明确有 `signature` 字段，注释写着"值为本次响应的消息 id"。

真实情况：
- 流式里 `signature_delta` 单独下发，必须存下来
- 下一轮要把 `{type:'thinking', thinking, signature}` 原样带回
- **没有签名的思考块不能带**，上游会 400

所以只回传带签名的。OpenAI 通道没有签名，那些思考块丢弃。

实测：第一轮 94 字符 reasoning + signature，第二轮正常完成、不再报错。

---

## R6 · 把 asr/tts/files 收进能力层

这三个一直在路由里直连 DeepSeek，能跑但换不了 provider——和 R2 的设计矛盾。

顺手修了一个接口设计错误：`TtsCapability` 原本要求返回**裸 PCM 流**，
但上游给的是容器格式（wav/mp3），唯一的消费者是 MediaPlayer。
强制 PCM 等于让客户端自己剥 RIFF 头，没有任何好处。改成 `{audio, mime}`。

---

## R7 · 历史 v3

第一版写了个自造的 `conversations.json`，chat-demo 根本读不了——它找 `manifest.json`。

照参考实现重写：

```
manifest.json           第一条目
chat_sessions/{id}.json 消息树 id + parent_id
chat_session_index.json 在全部会话之后
```

13 种 fragment 全部建模。导出只产生 REQUEST/RESPONSE/THINK/TOOL_SEARCH/FILE；
导入时 TIP、CONTENT_RISK、TOOL_OPEN、READ_LINK、TOOL_IMAGE_SEARCH 等**主动丢弃**
——它们在本端没有展示位置，硬塞进正文等于伪造内容。

实测：76 KB / 25 条目 / 22 会话 / 53 消息，导入回来 52 条（一条消息没有可渲染的
fragment，按设计跳过）。

---

## R8 · UI 细节修正

你两次否了我的实现，都是对的：

1. **中间那个圆环"纯粹为了模仿而模仿"** —— 原来是 CSS 渐变球 + 外圈，确实假。
   改成 fBm 噪声烘焙的纹理（768×768 RGBA），呼吸 3.4s / 摇摆 18s / 光晕 4.2s，
   周期故意拉长，让它像"活着"而不是"抢注意力"。
2. **语音页下面三个按钮和参考图不符** —— 参考图里紫色是**输入条内部的环**，
   不是外面另起一排。改掉了。

**教训**：照着截图做，要看清元素**属于哪个容器**，不是看它大致在哪个位置。

---

## R9 · Markdown 表格

Compose 没有内置表格，手搭：等宽列 + 表头底色 + 行分隔线。

踩了一个真 bug：第一版单元格直接传原文，`**Data Types**` 原样显示出来。
表格里的行内样式也要走 `renderInline`。

表格判定要求"表头 + 分隔行"，否则正文里一个 `|` 就被误认。

---

## R10 · 继续生成

两个问题，一张截图全暴露了：
1. 提示词被存成真的用户消息（会话里能看到「请直接接着你上一条回答…」）
2. 续写变成第二条回答气泡

修法：
- 后端不再落用户消息，提示词只进上游请求
- 前端渲染时把相邻 assistant 消息合并，接缝处按标点判断要不要换行

---

## R11 · APK 逆向

### 工具

REA CLI 装了，也按它的要求配了 apktool 路径（`REA_APKTOOL_COMMAND`），
但它报 `apktool launcher did not report a usable version`；换成 `java -jar` 形式
返回 `UNKNOWN`。根因是 Windows 上不能用 spawn 直接执行 `.bat`。**REA 的 Android
路径在这个平台跑不通**，改用 apktool + jadx，产出等价。

Ghidra 没装：它分析 `lib/*.so` 的原生代码，对 DEX 和 UI 都没帮助。

### 从 ChatGPT APK 得到什么

`res/values/colors.xml` 里的精确品牌色：

```
colorPrimary        #4700EB
colorPrimaryVariant #3000BB
colorSecondary      #AA85FF
```

**这推翻了我之前的假设**：我一直按截图取色用 `#8B5CF6`，实际是 `#4700EB`。
新版 ChatGPT 是紫色主题，不是老版的 `#10A37F` 绿。

界面文案（`strings.xml` 583 KB，key 被混淆成 `7c` 这种但 value 可读）：
`Ask ChatGPT` / `Voice search` / `Choose a voice` / `Search…`。

Activity 结构说明图像编辑、文本处理、文件预览是**独立页面**而不是弹窗：
`ImageEditActivity` / `TextProcessorActivity` / `FilePreviewActivity`。

**拿不到的**：Compose 组件树。601 个 layout 全是 `abc_*` 库布局 → UI 是纯 Compose；
反编译出的 87513 个 java 文件里 `com.openai.design` 只剩 4 个混淆文件。
字号/间距/圆角都在混淆代码里，静态提取成本极高、收益低。

**结论**：ChatGPT UI 只能靠截图 + 精确色板还原，不要试图硬啃反编译。

### 从 DeepSeek APK 得到什么

它用的是**私有 API**，不是 `api.deepseek.com`：

```
/api/v0/chat/completion        ← 带 PoW 挑战
/api/v0/chat/regenerate|continue|edit_message
/api/v0/asr/ws                 ← ASR 走 WebSocket 流式
/api/v0/index/prepare|query    ← 文件走 RAG 索引
/api/v0/users/*                ← 登录/短信验证码
```

`/api/v0/chat/completion` 发请求前要算工作量证明：
`PowCalculator.calculateDeepSeekHashV1Pow(String, String, long)`，还要减 20 秒余量。

**三条结论**：

1. **不能复用它的后端**。要登录态 + 动态 PoW + 无契约的私有接口，绑上去等于
   把命交给人家随时能改的东西。继续用公开 `api.deepseek.com` 是对的。
2. **它的语义可以借鉴**：regenerate / continue / edit_message 是三个独立端点，
   说明官方也是"在原消息上操作"，和我们的方向一致。
3. **有一个真实差距**：官方 ASR 是 WS 流式（`/api/v0/asr/ws`），公开 API 的
   `/v1/tools/asr` 只能整段上传。所以"边说边出字"做不到——这是接口能力差异，
   不是实现不到位。文件也一样：官方是 RAG 索引，我们是 Files API + file_id 引用。

---

## R12 · 全本地化

你问"为什么要服务器，我们不是在手机里的吗"——这个问题问到根上。

之前是 Gateway（Node）跑在电脑上，Android 只当客户端。结果就是用户装个 App
还得先跑服务、查 IP、填地址。这不是 App。

改成：**后端逻辑全部用 Kotlin 重写进 App**。

- `LocalStore` 手写 SQLite（两张表，不值得为它上 Room + KSP）
- `DirectChatApi` 两种协议直连
- `DirectToolsApi` 走 DeepSeek 的 `/v1/tools/*`
- `ChatRepository` 深搜 loop 在设备上跑
- `HistoryArchive` v3 zip 在手机上读写

**实测通过**：填智汇 key → 测试连接 → `say hi in three words` → `Hi there friend`
（还带折叠的思考过程）→ 杀进程重开，会话还在。

代价说清楚：密钥在设备里。个人用可以，要分发就得走服务端代持。

---

## 遗留

- 工具类（搜索/OCR/语音）只填智汇 key 时不工作——那些端点只有 DeepSeek 官方有
- 拍照在模拟器验证不了（`vendor.qemu.sf.fake_camera=none`，相机 app 启动即崩）
- 语音听感没在真机听过
- 单用户，没有登录
- 工具实现偏"直译"：参数（搜 8 条、抓 3 篇）是拍的，没有根据实际效果调过
