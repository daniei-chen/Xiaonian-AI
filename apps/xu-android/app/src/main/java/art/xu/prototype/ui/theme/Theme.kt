package art.xu.prototype.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class DiColors(
    val accent: Color,
    val canvas: Color,
    val ink: Color,
    val secondary: Color,
    val line: Color,
    val soft: Color,
    val peach: Color,
    val card: Color,
)

val LocalDiColors = staticCompositionLocalOf {
    DiColors(Color(0xFF4700EB), Color.White, Color(0xFF1B1B1B), Color(0xFF929396), Color(0xFFE5E7EC), Color(0xFFF5F6F8), Color(0xFFE9EEFF), Color.White)
}

private val light = lightColorScheme(
    primary = Color(0xFF4700EB),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFF5F6F8),
    onSurfaceVariant = Color(0xFF929396),
    outline = Color(0xFFE5E7EC),
)

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 25.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun XuTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = DiColors(light.primary, light.background, light.onBackground, light.onSurfaceVariant, light.outline, light.surfaceVariant, Color(0xFFF0EFEB), light.surface)
    androidx.compose.runtime.CompositionLocalProvider(LocalDiColors provides palette) {
        MaterialTheme(colorScheme = light, typography = typography, content = content)
    }
}
