package art.kaogong.xiaonian.ui.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 语音对话模式（全屏）。
 *
 * 版式严格照参考图：
 *   顶部  左「菜单」圆钮 · 右「语音设置」圆钮（都是浅灰实心圆，不是描边）
 *   中央  紫雾球：上方饱和紫，一道白色斜向絮带横穿，外面再套一圈极淡光晕
 *   底部  [ + 询问小念  (紫圆环) ]  静音  关闭 —— **框外只有两个按钮**
 *
 * 那个紫圆环是输入条内部的图标，不是第三个按钮。第一版做错就错在这里。
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
            SoftCircleButton(Icons.Outlined.Menu, "菜单", onOpenDrawer)
            SoftCircleButton(Icons.Outlined.Tune, "语音设置") {}
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
            // 输入条：+ / 文案 / 框内右侧的紫圆环
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
                Spacer(Modifier.weight(1f))
                PurpleRing()
            }

            Spacer(Modifier.width(10.dp))

            SolidCircleButton(
                icon = Icons.Outlined.MicOff,
                description = "静音",
                background = XiaonianColors.SurfaceMuted,
                tint = XiaonianColors.Ink,
                onClick = {},
            )

            Spacer(Modifier.width(10.dp))

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

/**
 * 紫雾球。
 *
 * 参考图里它不是一个"紫色圆"，而是：
 *  - 上半部饱和紫，向下迅速掉到近白
 *  - 一道白色絮状光带斜穿（这就是让它看起来像雾而不是像色块的关键）
 *  - 边缘是干净硬边，外面套一层几乎看不见的淡紫光晕
 *
 * 动效做了三层：整体呼吸、絮带极慢旋转、外光晕强弱。都是长周期，不能抢注意力。
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

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )

    val halo by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        // 外层光晕：比球大一圈，落在球外
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6).copy(alpha = halo),
                        Color(0xFF8B5CF6).copy(alpha = 0f),
                    ),
                    center = center,
                    radius = r * 1.42f,
                ),
                radius = r * 1.42f,
                center = center,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(CircleShape),
        ) {
            val r = size.minDimension / 2f
            val c = center

            val circle = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        left = c.x - r,
                        top = c.y - r,
                        right = c.x + r,
                        bottom = c.y + r,
                    ),
                )
            }

            clipPath(circle) {
                // 1) 底色：上偏右饱和紫，向左下迅速褪成近白
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA855F7),
                            Color(0xFFB98CFF),
                            Color(0xFFE9DBFF),
                            Color(0xFFFBF8FF),
                        ),
                        center = Offset(c.x + r * 0.28f, c.y - r * 0.42f),
                        radius = r * 1.55f,
                    ),
                    radius = r * 1.1f,
                    center = c,
                )

                // 2) 白色絮带：用压扁的椭圆叠出"丝带"，圆形光斑叠不出这个形状
                rotate(degrees = drift - 20f, pivot = c) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.82f),
                                Color.White.copy(alpha = 0.46f),
                                Color.White.copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0f),
                            ),
                            center = c,
                            radius = r * 0.70f,
                        ),
                        topLeft = Offset(c.x - r * 0.92f, c.y - r * 0.30f),
                        size = Size(r * 1.84f, r * 0.60f),
                    )
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.62f),
                                Color.White.copy(alpha = 0.28f),
                                Color.White.copy(alpha = 0f),
                            ),
                            center = c,
                            radius = r * 0.54f,
                        ),
                        topLeft = Offset(c.x - r * 0.60f, c.y - r * 0.02f),
                        size = Size(r * 1.36f, r * 0.46f),
                    )
                }

                // 3) 顶部压深紫，让上沿更"实"，与参考图的明暗交界一致
                translate(top = -r * 0.66f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF8B2FE0).copy(alpha = 0.46f),
                                Color(0xFF8B2FE0).copy(alpha = 0f),
                            ),
                            center = Offset(c.x, c.y),
                            radius = r * 0.98f,
                        ),
                        radius = r * 0.98f,
                        center = Offset(c.x, c.y),
                    )
                }
            }
        }
    }
}

/** 输入条内部右侧那个紫圆环（环 + 偏左下的小点） */
@Composable
private fun PurpleRing() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val r = size.minDimension / 2f
        drawCircle(
            color = XiaonianColors.Accent,
            radius = r - 1.5.dp.toPx(),
            style = Stroke(width = 2.2.dp.toPx()),
        )
        drawCircle(
            color = XiaonianColors.Accent,
            radius = r * 0.22f,
            center = Offset(size.width * 0.40f, size.height * 0.62f),
        )
    }
}

/** 顶部两个浅灰实心圆（参考图里不是描边圆） */
@Composable
private fun SoftCircleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFFF2F2F4))
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
