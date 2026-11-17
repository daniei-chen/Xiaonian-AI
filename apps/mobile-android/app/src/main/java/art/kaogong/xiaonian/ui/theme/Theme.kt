package art.kaogong.xiaonian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun XiaonianTheme(content: @Composable () -> Unit) {
    // colorScheme 必须在 Composition 内构造：强调色和明暗都来自 CompositionLocal
    val accent = LocalAccent.current
    val palette = LocalPalette.current
    // 色板自己决定明暗，不用另开一个布尔量，避免两处状态不同步
    val isDark = palette.canvas.luminance() < 0.5f

    val scheme = if (isDark) {
        darkColorScheme(
            primary = accent.accent,
            onPrimary = Color.White,
            secondary = accent.accent,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.canvas,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceMuted,
            onSurfaceVariant = palette.inkSecondary,
            outline = palette.divider,
        )
    } else {
        lightColorScheme(
            primary = accent.accent,
            onPrimary = Color.White,
            secondary = accent.accent,
            background = palette.canvas,
            onBackground = palette.ink,
            surface = palette.canvas,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceMuted,
            onSurfaceVariant = palette.inkSecondary,
            outline = palette.divider,
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = XiaonianTypography,
        content = content,
    )
}
