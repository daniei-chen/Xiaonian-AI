package art.kaogong.xiaonian.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 从 ChatGPT Android 当前版本截图取色。
 * 这一组是小念的视觉基线：几乎没有色，唯一的彩色是紫色强调。
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

    /** 唯一强调色 */
    val Accent = Color(0xFF8B5CF6)
    val AccentDeep = Color(0xFF7C3AED)
}
