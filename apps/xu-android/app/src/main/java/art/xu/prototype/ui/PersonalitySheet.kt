package art.xu.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalitySheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalDiColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.canvas, shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
            Text("你想让小 D 怎么陪你聊？", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("换一种语气，不改变事实和回答质量。", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
            Spacer(Modifier.height(22.dp))
            listOf(
                Triple("温暖", "先听你说，再一起想", "像和一位懂你的朋友聊天"),
                Triple("直接", "少一点铺垫，多一点重点", "快速抵达问题的核心"),
                Triple("好奇", "多问一个为什么", "一起发现没想到的角度"),
            ).forEach { (title, subtitle, detail) ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(if (title == current) colors.peach else colors.soft).quietClick { onSelect(title) }.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(5.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
                    }
                    if (title == current) Icon(Icons.Outlined.Check, "当前语气", tint = colors.accent)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text("仅切换预览风格，后端接入后用于系统提示词。", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
        }
    }
}
