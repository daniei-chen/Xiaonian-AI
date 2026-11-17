# 第一阶段状态（DeepSeek 功能对齐）

> 判定标准：用户能在小念里完成 DeepSeek App 的主要工作。
> 最后更新：2026-11-17

## 逐项状态

| # | 目标 | 状态 | 实现位置 | 验证方式 |
|---|---|---|---|---|
| 1 | 聊天 | ✅ | `POST /conversations/:id/messages`（SSE）+ Android 流式渲染 | 26 个 delta 落库，seq 连续 |
| 2 | 深度思考 | ✅ | `thinking` + `effort`(high/max)；思考块带 signature 回传 | 两轮思考对话，第二轮无 400，reasoning 94ch + signature YES |
| 3 | 搜索 | ✅ | `DeepSeekSearchCapability` → `/v1/tools/search`，**不再透传上游 tools** | 8 条引用 + 回答带 `[2][4]` 标记 |
| 4 | 深度搜索 | ✅ | `apps/gateway/src/search/deep-search.ts` 自建 loop | 8 条引用 + 回答带 `[2][7]` 标记 |
| 5 | 读链接 | ✅ | `DeepSeekFetchCapability` → `/v1/tools/fetch`；UI 从剪贴板取 URL | 2583 字符正文 + 标题 |
| 6 | 文件问答 | ✅ | `/api/v1/files` → DeepSeek Files API，`file_id` 引用 | 上传 PNG 问 secret word，答 PANGOLIN |
| 7 | OCR | ✅ | `DeepSeekOcrCapability` → `/v1/tools/ocr`；UI「图片转文字」 | 识别出 PANGOLIN 与日期 |
| 8 | ASR | ✅ | `AudioRecord` → `/api/v1/asr` → 填输入框 | TTS 生成「今天下午三点提醒我开会」，ASR 原样返回 |
| 9 | TTS | ✅ | `/api/v1/tts` + 消息「朗读」+ 语音页自动朗读 | 178 KB RIFF |
| 10 | 引用 | ✅ | `citations` 事件 + 来源卡片；deep search 自带编号 | 8 条来源卡片渲染 |
| 11 | 历史导入 | ✅ | `src/lib/zip.ts` 读写 + `/api/v1/export` `/import` | 7 会话 17 消息 → 13.5 KB → 导入计数一致 |
| 12 | 移动端使用 | ✅ | Compose + OkHttp，`10.0.2.2:8788` | 真机/模拟器可装可跑 |

## 实现要点

1. **思考块回传带签名。** Anthropic 协议要求 thinking 块连同 `signature`（即该次
   响应的消息 id）一起回传，缺签名会被拒绝。所以流式时收集 `signature_delta` 并挂
   回思考块，历史里只回传**带签名**的思考块——没签名的（比如 OpenAI 通道返回的）
   直接丢弃，否则上游报 400。

2. **两条搜索路都是自己的实现。** `webSearch` 与 `deepSearch` 现在共用同一套
   capability 层 loop，唯一区别是抓不抓正文（`maxFetch` 0 vs 3）。不再有
   "把 tools 透传给上游" 的路径，因此搜索可以在配置里换成 anysearch 之类的 provider。

3. **续写不落假消息。** 继续生成的提示词只发给上游，不写进历史；产生的第二条
   assistant 消息在 UI 上并回上一条，用户看到的是"回答被补全"。

## 架构现状（重要）

`config/capabilities.yaml` 声明 7 种能力，实际装配情况：

| 能力 | 是否有 provider | 是否走 CapabilityRegistry |
|---|---|---|
| chat | ✅ openai / anthropic 两个 driver | ✅ |
| search | ✅ deepseek | ✅ |
| fetch | ✅ deepseek | ✅ |
| ocr | ✅ deepseek | ✅ |
| asr | ✅ deepseek | ✅ |
| tts | ✅ deepseek | ✅ |
| files | ✅ deepseek | ✅ |

**asr / tts / files 已补进能力层**：三者现在都是 registry 里的 provider，
`/meta/capabilities` 七种能力齐全，换供应商只改配置。

## 已知限制

- ZIP 只回传 text / reasoning / citations 三类内容；TIP、CONTENT_RISK、
  TOOL_OPEN、READ_LINK、TOOL_IMAGE_SEARCH 等按设计丢弃，不伪装成正文
- 语音页未在真机验证听感（模拟器麦克风静音）
- 拍照走系统相机 Intent，但本机 AVD 的 `vendor.qemu.sf.fake_camera=none`，
  相机 app 启动即崩，拍照链路只能在真机验证
- 单用户（`DEV_USER_ID`），认证按 spec 8.1 未实现

## 验证脚本

```bash
pnpm --filter @xiaonian/gateway start   # 或 node apps/gateway/dist/index.js
node --env-file=.env scripts/e2e-chat.mjs        # 聊天 + 落库
node --env-file=.env scripts/e2e-tools.mjs       # search / fetch / ocr / deep search
node --env-file=.env scripts/e2e-file.mjs        # 文件上传 + 图文问答
node --env-file=.env scripts/e2e-voice-search.mjs # TTS→ASR 闭环 + 引用
node --env-file=.env scripts/e2e-replay.mjs      # 重新生成 / 继续生成
```
