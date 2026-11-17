package art.kaogong.xiaonian.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 中性色板（背景、文字、分隔线）。
 *
 * 强调色单独由 [AccentTheme] 提供；这里只管明暗。
 * 之所以不用 MaterialTheme.colorScheme：组件里大量直接用 `LocalPalette.current.ink`
 * 这种写法，绕过了主题，深色模式下会变成黑底黑字。
 */
data class Palette(
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val canvas: Color,
    val surfaceMuted: Color,
    val surfaceRaised: Color,
    val divider: Color,
    /** 卡片底（引用卡片、代码块） */
    val surfaceCard: Color,
) {
    companion object {
        /** 参考截图实测值 */
        val Light = Palette(
            ink = Color(0xFF0D0D0D),
            inkSecondary = Color(0xFF8A8A8E),
            inkTertiary = Color(0xFFB9B9BE),
            canvas = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFF4F4F5),
            surfaceRaised = Color(0xFFEFEFF1),
            divider = Color(0xFFE8E8EA),
            surfaceCard = Color(0xFFF7F7F8),
        )

        val Dark = Palette(
            ink = Color(0xFFECECEC),
            inkSecondary = Color(0xFF9A9AA0),
            inkTertiary = Color(0xFF6E6E75),
            canvas = Color(0xFF000000),
            surfaceMuted = Color(0xFF1C1C1E),
            surfaceRaised = Color(0xFF2C2C2E),
            divider = Color(0xFF2E2E32),
            surfaceCard = Color(0xFF1C1C1E),
        )
    }
}

/** 当前明暗色板；由 MainActivity 根据设置提供 */
val LocalPalette = staticCompositionLocalOf { Palette.Light }
