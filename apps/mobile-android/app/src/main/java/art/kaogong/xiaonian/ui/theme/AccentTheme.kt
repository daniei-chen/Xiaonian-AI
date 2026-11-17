package art.kaogong.xiaonian.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 可选的强调色主题。
 *
 * 参考图里的人给的紫色是 `#8B5CF6`；APK 资源里另有一套 `#4700EB`，
 * 说明上游本身就换过主题色。所以颜色不该写死，做成可选项。
 *
 * 只换强调色，中性色（白底、灰面、近黑字）保持不变——那是版式的基础。
 */
enum class AccentTheme(
    val id: String,
    val label: String,
    val accent: Color,
    val deep: Color,
    val soft: Color,
) {
    Blue("blue", "蓝", Color(0xFF3367F1), Color(0xFF2554D8), Color(0xFFBFD0FF)),
    Purple("purple", "紫", Color(0xFF8B5CF6), Color(0xFF7C3AED), Color(0xFFC4B5FD)),
    Black("black", "黑", Color(0xFF1C1C1E), Color(0xFF000000), Color(0xFFD1D1D6)),
    Green("green", "绿", Color(0xFF10A37F), Color(0xFF0D8A6A), Color(0xFFA7E8D6)),
        Orange("orange", "橙", Color(0xFFF97316), Color(0xFFEA580C), Color(0xFFFED7AA)),
    ;

    companion object {
        fun from(id: String?): AccentTheme =
            entries.firstOrNull { it.id == id } ?: Blue
    }
}

/** 当前强调色；由 XiaonianApp 根据设置提供 */
val LocalAccent = staticCompositionLocalOf { AccentTheme.Blue }
