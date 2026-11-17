package art.kaogong.xiaonian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun XiaonianTheme(content: @Composable () -> Unit) {
    // colorScheme 必须在 Composition 内构造：强调色来自 CompositionLocal
    val accent = LocalAccent.current
    val scheme = lightColorScheme(
        primary = accent.accent,
        onPrimary = Color.White,
        secondary = accent.accent,
        background = XiaonianColors.Canvas,
        onBackground = XiaonianColors.Ink,
        surface = XiaonianColors.Canvas,
        onSurface = XiaonianColors.Ink,
        surfaceVariant = XiaonianColors.SurfaceMuted,
        onSurfaceVariant = XiaonianColors.InkSecondary,
        outline = XiaonianColors.Divider,
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = XiaonianTypography,
        content = content,
    )
}
