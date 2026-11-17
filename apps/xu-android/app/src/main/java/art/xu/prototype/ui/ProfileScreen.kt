package art.xu.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun ProfileScreen(state: DiState, onBack: () -> Unit, onDark: () -> Unit, onReducedMotion: () -> Unit, onPersonality: () -> Unit) {
    val colors = LocalDiColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(63.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.ink) }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(horizontal = 21.dp)) {
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DiMark(Modifier.size(47.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("小 D", style = MaterialTheme.typography.titleLarge)
                    Text("一个有回应，也有余地的 AI 伙伴。", color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(colors.card).quietClick(onPersonality).padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("小 D 的性格", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    Text("选择聊天时的语气，不改变事实。", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
                }
                Text(state.personality + " ›", color = colors.accent, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(19.dp), color = colors.card) {
                Column(Modifier.padding(horizontal = 19.dp)) {
                    PreferenceRow("减少动态效果", state.reducedMotion, onReducedMotion)
                }
            }
            Spacer(Modifier.height(25.dp))
            Text("当前版本", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
            Spacer(Modifier.height(9.dp))
            Text("前端设计预览 0.1.0\n\n聊天、附件与语音使用本地演示数据；不含网络请求、账户或真实 AI 服务。偏好与会话修改暂不持久保存。", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
        }
    }
}

@Composable
private fun PreferenceRow(title: String, checked: Boolean, onChange: () -> Unit) {
    val colors = LocalDiColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, { onChange() }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, uncheckedTrackColor = colors.line, uncheckedBorderColor = colors.line))
    }
}
