package art.xu.prototype.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun VoiceScreen(state: DiState, onToggle: () -> Unit, onClose: () -> Unit) {
    val colors = LocalDiColors.current
    val transition = rememberInfiniteTransition(label = "voice")
    val phase by transition.animateFloat(initialValue = 0.8f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "breath")
    val amount = if (state.reducedMotion) 0.85f else phase
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Box(Modifier.fillMaxWidth().height(63.dp)) {
            IconButton(onClose, Modifier.align(Alignment.CenterStart)) { Icon(Icons.Filled.Close, "关闭语音", tint = colors.ink) }
            Text("和小 D 说说话", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.8f))
            Canvas(Modifier.size(220.dp)) {
                drawCircle(colors.soft, radius = size.width * 0.47f * amount)
                drawCircle(colors.accent, radius = size.width * 0.34f)
                val heights = listOf(0.08f, 0.14f, 0.22f, 0.15f, 0.25f, 0.16f, 0.09f)
                heights.forEachIndexed { index, height ->
                    val x = size.width * (0.30f + index * 0.067f)
                    val y = size.height * height * (if (state.listening) amount else 0.75f)
                    drawLine(colors.canvas, Offset(x, center.y - y / 2), Offset(x, center.y + y / 2), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            Spacer(Modifier.height(35.dp))
            Text(if (state.listening) "我在听。" else "不用组织语言，\n想到哪儿说到哪儿。", style = MaterialTheme.typography.headlineLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(13.dp))
            Text(if (state.listening) "说完后，点下方结束。" else "声音是另一种，自然的交流。", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
            Spacer(Modifier.weight(1f))
            Text("语音界面演示 · 不采集麦克风，不识别语音", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.size(66.dp).clip(CircleShape).background(colors.accent).quietClick(onToggle), contentAlignment = Alignment.Center) {
                Icon(if (state.listening) Icons.Filled.Stop else Icons.Filled.Mic, if (state.listening) "结束语音演示" else "开始语音演示", tint = colors.canvas, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
