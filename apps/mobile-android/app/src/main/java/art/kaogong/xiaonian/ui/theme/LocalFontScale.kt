package art.kaogong.xiaonian.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 回答正文字号倍率。
 *
 * 用 CompositionLocal 而不是一路传参：从设置页到消息气泡中间隔了
 * XiaonianApp → ChatScreen → MessageList → AssistantMessage → MarkdownText，
 * 每一层都加一个 Float 参数没有意义。
 */
val LocalFontScale = staticCompositionLocalOf { 1f }
