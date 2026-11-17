# APK 逆向记录（ChatGPT / DeepSeek）

工具与结论，以及**为什么某些东西拿不到**。

## 工具链

| 工具 | 状态 |
|---|---|
| REA CLI 6.4.0 | 已装，但 Android 解码在 Windows 上跑不通 |
| apktool 2.11.1 | 可用（`.ref/apk-tools/apktool.jar`） |
| jadx 1.5.1 | 可用（`.ref/apk-tools/bin/jadx.bat`） |

### REA 为什么没用上

`rea doctor` 显示缺少 Ghidra / Hopper / IDA，但这三者都是**原生二进制**分析引擎，
对 APK 里的 DEX 没有帮助。REA 的 Android 能力实际走 apktool：

1. 设了 `REA_APKTOOL_COMMAND` 后 REA 能找到 apktool，但报
   `apktool launcher did not report a usable version`
2. 根因是 Windows 上 `.bat` 不能直接 spawn（REA 没走 `cmd /c`）；
   改成 `java -jar ...` 形式后返回 `UNKNOWN`，仍失败

所以 REA 的 Android 路径在当前平台不可用，改用 apktool + jadx 直接做，
产出等价（下面的数据就是这样拿到的）。

Ghidra 没配：要 JDK 21 + 约 400 MB 安装包，而它分析的是 `lib/*.so`，
不是 UI 也不是业务逻辑——对本任务没有收益。

## ChatGPT（com.openai.chatgpt）

### 拿到的东西

**品牌色（`res/values/colors.xml`，精确值）**

```
colorPrimary        #4700EB   ← 主色，深靛紫
colorPrimaryVariant #3000BB
colorSecondary      #AA85FF   ← 浅紫，用于高亮/次级元素
background          #FFFFFF
grayButton          #3C3E49
```

注意这**不是**老版 ChatGPT 的绿色 `#10A37F`，用户给的是新版紫色主题。

**界面文案（`strings.xml`，583 KB，名字被混淆成 `7c` 这种但值可读）**

```
Ask ChatGPT / Voice search / Choose a voice
Voice info
Search… / Search folder / Search library / Search Space
Ask questions, get summaries, and more with your files.
```

**Activity 结构**：`MainActivity` + 独立功能的 Activity（`ImageEditActivity`、
`TextProcessorActivity`、`FilePreviewActivity`、`AssistantProxyActivity` …），
说明图像编辑、文本处理、文件预览都是独立页面而不是弹窗。

### 拿不到的东西

- **Compose 组件树**：601 个 layout 里没有一个是聊天界面（全是 `abc_*` 库布局），
  说明 UI 是纯 Compose；而反编译出的 87513 个 java 文件里，
  `com.openai.design` 只剩 4 个混淆文件，`MaterialTheme` 定义找不到。
- **字号 / 间距 / 圆角**：同上，都在被混淆的 Compose 代码里，静态提取成本极高。

**结论**：ChatGPT UI 的还原应该基于截图 + 上面的精确色板，而不是硬啃反编译。

## DeepSeek（com.deepseek.chat）

### 关键发现：用的是私有 API，不是公开 API

```
/api/v0/chat/completion        ← 聊天（含 PoW 挑战）
/api/v0/chat/regenerate        ← 重新生成
/api/v0/chat/continue          ← 继续生成
/api/v0/chat/edit_message      ← 编辑消息
/api/v0/chat/history_messages
/api/v0/chat_session/create
/api/v0/chat/status
/api/v0/chat/tts/voices
/api/v0/asr/ws                 ← ASR 走 WebSocket 流式
/api/v0/index/prepare|query    ← 文件索引（RAG）
/api/v0/export_all
/api/v0/users/*                ← 登录/注册/短信验证码
```

**`/api/v0/chat/completion` 带 PoW 风控**：
`com.deepseek.crypto.PowCalculator.calculateDeepSeekHashV1Pow(String,String,long)`
在发请求前算一个工作量证明，还要减去 20 秒余量。

### 对实现的影响

1. **不能复用它的后端**：需要登录态 + 动态 PoW，且是随时会变的私有接口。
   逆向接入等于把自己绑在一个没有契约的目标上。
2. **继续用公开 API**（`api.deepseek.com/v1` + `/anthropic/v1`）：这是我们
   已经在用的路，能力覆盖相同。
3. **可以借鉴的语义**：它把 regenerate / continue / edit_message 做成三个
   独立端点，说明官方也是"在原消息上操作"——和我们的实现方向一致。
4. **ASR 是 WebSocket 流式**（`/api/v0/asr/ws`）。我们用公开 API 的
   `/v1/tools/asr` 只能整段上传，做不到边说边出字。这是**公开 API 的真实差距**，
   不是我们实现不到位。
5. **文件是 RAG 索引**（`/api/v0/index/prepare` + `/query`），不是把全文塞进
   上下文。我们走 Files API + file_id 引用，路径不同但结果相当。

## 产出文件

```
.ref/apk/DeepSeek/           apktool 解出的资源与 manifest
.ref/apk/DeepSeek-src/       jadx 反编译（8940 类，68 个失败）
.ref/apk/ChatGPT/            apktool 资源（601 layout / 583 KB strings）
.ref/apk/ChatGPT-src/        jadx 反编译（58237 类，415 个失败）
```
