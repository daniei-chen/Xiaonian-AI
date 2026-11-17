package art.kaogong.xiaonian.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 从 ChatGPT Android 的 APK 资源里取到的精确品牌色
 * （`res/values/colors.xml`：colorPrimary / colorPrimaryVariant / colorSecondary），
 * 其余中性色沿用截图取色。
 *
 * 注意：新版 ChatGPT 是紫色主题，不是老版的绿色 #10A37F。
 */
object XiaonianColors {
    /** 主文字：近黑，不用纯黑以免发闷 */
    val Ink = Color(0xFF0D0D0D)

    /** 次要文字 / 未选中分段 */
    val InkSecondary = Color(0xFF8A8A8E)

    /** 占位符、极弱提示 */
    val InkTertiary = Color(0xFFB9B9BE)

    /** 页面底色 */
    val Canvas = Color(0xFFFFFFFF)

    /** 输入条、圆形按钮底 */
    val SurfaceMuted = Color(0xFFF4F4F5)

    /** 分段控件轨道 */
    val SurfaceRaised = Color(0xFFEFEFF1)

    val Divider = Color(0xFFE8E8EA)

    /** 唯一强调色 —— ChatGPT 的 colorPrimary */
    val Accent = Color(0xFF4700EB)

    /** 按下态 / 深色变体 —— colorPrimaryVariant */
    val AccentDeep = Color(0xFF3000BB)

    /** 浅紫，用于选中底、角标 —— colorSecondary */
    val AccentSoft = Color(0xFFAA85FF)
}
