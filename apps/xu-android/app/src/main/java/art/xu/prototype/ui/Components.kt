package art.xu.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun Modifier.quietClick(onClick: () -> Unit) = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

@Composable
fun DiMark(modifier: Modifier = Modifier, color: Color = LocalDiColors.current.accent) {
    CanvasMark(modifier, color)
}

@Composable
private fun CanvasMark(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val unit = size.minDimension / 100f
        val first = Path().apply {
            moveTo(28 * unit, 15 * unit)
            lineTo(72 * unit, 15 * unit)
            cubicTo(89 * unit, 15 * unit, 89 * unit, 29 * unit, 89 * unit, 45 * unit)
            cubicTo(89 * unit, 61 * unit, 81 * unit, 69 * unit, 64 * unit, 69 * unit)
            lineTo(48 * unit, 69 * unit)
            lineTo(33 * unit, 82 * unit)
            lineTo(33 * unit, 69 * unit)
            lineTo(28 * unit, 69 * unit)
            cubicTo(11 * unit, 69 * unit, 11 * unit, 57 * unit, 11 * unit, 42 * unit)
            cubicTo(11 * unit, 26 * unit, 14 * unit, 15 * unit, 28 * unit, 15 * unit)
        }
        val second = Path().apply {
            moveTo(47 * unit, 87 * unit)
            lineTo(72 * unit, 87 * unit)
            lineTo(72 * unit, 97 * unit)
            lineTo(87 * unit, 87 * unit)
        }
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.5f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawPath(first, color, style = stroke)
        drawPath(second, color, style = stroke)
    }
}

@Composable
fun DiTopBar(title: String, onDrawer: () -> Unit, onNewChat: () -> Unit, onTitle: () -> Unit = {}) {
    val colors = LocalDiColors.current
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
        Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(18.dp)).background(colors.soft)) {
            Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("聊天", style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(colors.card).padding(horizontal = 17.dp, vertical = 6.dp))
                Text("工作", style = MaterialTheme.typography.labelLarge, color = colors.secondary, modifier = Modifier.quietClick(onTitle).padding(horizontal = 17.dp, vertical = 6.dp))
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.soft).quietClick(onDrawer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Menu, "会话与菜单", tint = colors.ink, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.soft).quietClick(onNewChat), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Edit, "新对话", tint = colors.ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun DiComposer(
    state: DiState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttach: () -> Unit,
    onSearch: () -> Unit,
    onThink: () -> Unit,
    onRemoveAttachment: () -> Unit,
) {
    val colors = LocalDiColors.current
    val focusRequester = androidx.compose.ui.focus.FocusRequester()
    Column(Modifier.fillMaxWidth()) {
        if (state.attachmentName != null) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(9.dp)).background(colors.soft).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.attachmentName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("移除", color = colors.secondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.quietClick(onRemoveAttachment).padding(4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(colors.soft).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, "附件与更多能力", tint = colors.ink, modifier = Modifier.size(44.dp).quietClick(onAttach).padding(11.dp))
                BasicTextField(
                    value = state.input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f).padding(vertical = 15.dp).focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    maxLines = 5,
                    decorationBox = { inner ->
                        Box {
                            if (state.input.isEmpty()) Text("询问 小 D", color = Color(0xFFB9B9BE), style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                    },
                )
                Icon(Icons.Filled.Mic, "语音输入", tint = colors.ink, modifier = Modifier.size(44.dp).quietClick(onVoice).padding(11.dp))
            }
            Spacer(Modifier.width(8.dp))
            val empty = state.input.isBlank() && state.attachmentName == null
            Box(Modifier.size(48.dp).clip(CircleShape).background(colors.accent).quietClick(if (empty) onVoice else onSend), contentAlignment = Alignment.Center) {
                Icon(if (empty) Icons.Outlined.GraphicEq else Icons.Outlined.ArrowUpward, if (empty) "语音对话" else "发送", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
    if (!state.referenceHome && state.activeId == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

@Composable
fun CapabilityChip(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val colors = LocalDiColors.current
    Row(Modifier.clip(CircleShape).background(if (active) colors.peach else colors.soft).quietClick(onClick).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.accent)
    }
}

@Composable
fun DiSnackbar(message: String, onDismiss: () -> Unit) {
    val colors = LocalDiColors.current
    Surface(color = colors.ink, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = colors.canvas, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("知道了", color = colors.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.quietClick(onDismiss).padding(5.dp))
        }
    }
}
