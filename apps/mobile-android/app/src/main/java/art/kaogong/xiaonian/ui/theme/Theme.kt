package art.kaogong.xiaonian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XiaonianColorScheme = lightColorScheme(
    primary = XiaonianColors.Accent,
    onPrimary = Color.White,
    secondary = XiaonianColors.AccentDeep,
    background = XiaonianColors.Canvas,
    onBackground = XiaonianColors.Ink,
    surface = XiaonianColors.Canvas,
    onSurface = XiaonianColors.Ink,
    surfaceVariant = XiaonianColors.SurfaceMuted,
    onSurfaceVariant = XiaonianColors.InkSecondary,
    outline = XiaonianColors.Divider,
)

@Composable
fun XiaonianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = XiaonianColorScheme,
        typography = XiaonianTypography,
        content = content,
    )
}
