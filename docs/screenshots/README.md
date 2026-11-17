# 截图

## 参考图（ChatGPT Android，用户提供）

用来对齐 UI 的原始截图，不是我们的实现。

| 文件 | 内容 |
|---|---|
| `android-chat.png` | 主聊天页：顶部分段「聊天 / 工作」、消息气泡、底部输入条 |
| `android-drawer.png` | 抽屉：定时任务 / 资料空间 / 自定义 / 设置 + 会话列表 |
| `android-messages.png` | 消息列表页 |
| `android-live-chat.png` | 流式输出中的状态 |
| `android-voice-mode.png` | 语音模式：**紫色环在输入条内部**，不是外挂一排按钮 |
| `android-citations.png` | 引用来源卡片 |
| `android-markdown-citations.png` | Markdown 正文 + 行内引用标记 |
| `android-attachment.png` | 附件面板：拍照 / 照片 / 文件 / 联网搜索 / 深入思考 |

## 我们的实现（验证用）

| 文件 | 内容 |
|---|---|
| `android-deep-search.png` | 深度搜索实测：8 条来源卡片 + 带 `[n]` 标记的回答 |

## 色板来源

`res/values/colors.xml`（从 ChatGPT.apk 反编译得到，不是截图取色）：

```
colorPrimary        #4700EB
colorPrimaryVariant #3000BB
colorSecondary      #AA85FF
```

详见 `docs/apk-reverse-notes.md`。
