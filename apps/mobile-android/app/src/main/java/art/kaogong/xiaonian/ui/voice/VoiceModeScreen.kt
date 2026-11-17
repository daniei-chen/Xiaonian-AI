package art.kaogong.xiaonian.ui.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.R
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 语音对话模式（全屏）。
 *
 * 版式严格照参考图：
 *   顶部  左「菜单」圆钮 · 右「语音设置」圆钮（浅灰实心，不是描边）
 *   中央  紫雾球 —— 纹理由 scripts/gen_voice_orb.py 烘焙，运行时只做变换
 *   底部  [ + 询问小念  (紫圆环) ]  静音  关闭 —— **框外只有两个按钮**
 *
 * 球用纹理而不是 Canvas 渐变：参考图的云雾是不规则絮理，渐变叠不出那个形状。
 */
@Composable
fun VoiceModeScreen(
    onClose: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(XiaonianColors.Canvas),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PressableCircle(size = 48.dp, background = Color(0xFFF2F2F4), onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "菜单",
                    tint = XiaonianColors.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            PressableCircle(size = 48.dp, background = Color(0xFFF2F2F4), onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "语音设置",
                    tint = XiaonianColors.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        VoiceOrb(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.60f)
                .aspectRatio(1f),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(XiaonianColors.SurfaceMuted)
                    .padding(start = 6.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 20.dp, color = Color.Black),
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "添加",
                        tint = XiaonianColors.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = "询问 小念",
                    style = MaterialTheme.typography.bodyLarge,
                    color = XiaonianColors.InkTertiary,
                )
                Spacer(Modifier.weight(1f))
                PurpleRing()
            }

            Spacer(Modifier.width(10.dp))

            PressableCircle(size = 52.dp, background = XiaonianColors.SurfaceMuted, onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.MicOff,
                    contentDescription = "静音",
                    tint = XiaonianColors.Ink,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(10.dp))

            PressableCircle(size = 52.dp, background = XiaonianColors.Ink, onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭语音对话",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * 紫雾球。
 *
 * 纹理负责"云雾"，动效负责"活"：
 *  - 呼吸 0.975~1.025，3.4s
 *  - 极缓左右摇摆 ±3.5°，18s 往复（整张图转 360° 会看出是死图）
 *  - 外光晕强弱脉动，4.2s
 * 周期都拉得很长，目的是"在动"而不是"吸引注意"。
 */
@Composable
private fun VoiceOrb(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")

    val breath by transition.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val sway by transition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    val halo by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6).copy(alpha = halo),
                        Color(0xFF8B5CF6).copy(alpha = 0f),
                    ),
                    center = center,
                    radius = r * 1.34f,
                ),
                radius = r * 1.34f,
                center = center,
            )
        }

        Image(
            painter = painterResource(R.drawable.voice_orb),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                    rotationZ = sway
                },
        )
    }
}

/** 输入条内部右侧那个紫圆环（环 + 偏左下的小点） */
@Composable
private fun PurpleRing() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            color = XiaonianColors.Accent,
            radius = r - 1.4.dp.toPx(),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = XiaonianColors.Accent,
            radius = r * 0.21f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.62f),
        )
    }
}

/**
 * 圆形按钮的统一质感：
 *  - 按下缩放 0.92 + 弹回（spring），比单纯涟漪更"实"
 *  - 深色按钮带投影，浅色按钮只留一丝，避免糊成一片
 *  - 涟漪半径按按钮尺寸给，不从角落扩散
 */
@Composable
private fun PressableCircle(
    size: Dp,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1200f),
        label = "pressScale",
    )

    val isDark = background == XiaonianColors.Ink

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isDark) 7.dp else 1.5.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.06f),
            )
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = ripple(radius = size / 2, color = Color.Black),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
