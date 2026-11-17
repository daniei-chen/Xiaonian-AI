package art.kaogong.xiaonian.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 从参考截图逐像素取色（1080×1920）。
 *
 * 注意：之前我按 APK 的 colors.xml 把主色改成 #4700EB，那是错的——
 * 参考截图里的紫色是 #8B5CF6。复刻的目标是截图，不是别的版本的资源表。
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

    /** 输入条、圆形按钮底 —— 截图实测 #F4F4F5 */
    val SurfaceMuted = Color(0xFFF4F4F5)

    /** 分段控件轨道 —— 截图实测 #EFEFF1 */
    val SurfaceRaised = Color(0xFFEFEFF1)

    val Divider = Color(0xFFE8E8EA)

    /** 唯一强调色 —— 截图实测 #8B5CF6（不是 APK 资源里的 #4700EB） */
    val Accent = Color(0xFF8B5CF6)

    /** 按下态 / 深色变体 */
    val AccentDeep = Color(0xFF7C3AED)

    /** 浅紫，用于选中底、角标 */
    val AccentSoft = Color(0xFFC4B5FD)
}
