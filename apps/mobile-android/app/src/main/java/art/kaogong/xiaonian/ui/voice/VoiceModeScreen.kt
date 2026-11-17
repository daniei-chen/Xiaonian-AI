package art.kaogong.xiaonian.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 语音对话模式（全屏）。
 *
 * 这是"紫色圆按钮"的去处，和输入框内的语音输入不是一回事：
 * 语音输入只是把话转成文字，这里是双向语音对话。
 *
 * P0 阶段先做视觉骨架（对应参考截图 5），ASR/TTS 接在 WP10/WP11。
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
        // 顶部：虚线圆环包着的菜单与设置
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DashedCircleButton(Icons.Outlined.Menu, "菜单", onOpenDrawer)
            DashedCircleButton(Icons.Outlined.Tune, "语音设置") {}
        }

        // 中央光晕：外层柔光 + 内层实体球，一起做极缓的呼吸
        val transition = rememberInfiniteTransition(label = "breath")
        val breath by transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathScale",
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.66f)
                .aspectRatio(1f)
                .scale(breath),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.35f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2E8B5CF6), Color(0x008B5CF6)),
                        ),
                        shape = CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFCBA9FF), Color(0xFFA97BFF), Color(0xFF8B5CF6)),
                            center = Offset(0.36f, 0.3f),
                        ),
                    ),
            )
        }

        // 底部：输入条 + 说话 / 静音 / 关闭
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
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {},
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
            }

            Spacer(Modifier.width(8.dp))
            SolidCircleButton(
                icon = Icons.Outlined.GraphicEq,
                description = "正在听",
                background = XiaonianColors.Accent,
                tint = Color.White,
                onClick = {},
            )
            Spacer(Modifier.width(8.dp))
            SolidCircleButton(
                icon = Icons.Outlined.MicOff,
                description = "静音",
                background = XiaonianColors.SurfaceMuted,
                tint = XiaonianColors.Ink,
                onClick = {},
            )
            Spacer(Modifier.width(8.dp))
            SolidCircleButton(
                icon = Icons.Outlined.Close,
                description = "关闭语音对话",
                background = XiaonianColors.Ink,
                tint = Color.White,
                onClick = onClose,
            )
        }
    }
}

/** 截图里顶部那两个是描边虚线圆环，不是填充底色 */
@Composable
private fun DashedCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .drawBehind {
                drawCircle(
                    color = Color(0xFFE2E2E4),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = XiaonianColors.Ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SolidCircleButton(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 语音页的空白占位，给未来接字幕留位置 */
@Composable
private fun TranscriptPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "",
            style = MaterialTheme.typography.bodyLarge,
            color = XiaonianColors.InkSecondary,
        )
    }
}
