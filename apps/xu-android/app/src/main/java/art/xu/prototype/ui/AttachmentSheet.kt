package art.xu.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(state: DiState, onDismiss: () -> Unit, onSearch: () -> Unit, onThink: () -> Unit, onFile: (String) -> Unit, onUnavailable: (String) -> Unit) {
    val colors = LocalDiColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.canvas, shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            AttachmentRow(Icons.Outlined.PhotoCamera, "拍照", "选择后模拟附件，暂不启动相机") { onFile("照片_今天.jpg") }
            AttachmentRow(Icons.Outlined.Image, "照片", "用于一起看图、提问") { onFile("图片_预览.png") }
            AttachmentRow(Icons.Outlined.AttachFile, "文件", "文档阅读与问答") { onFile("阅读材料.pdf") }
            AttachmentRow(Icons.Outlined.Link, "读链接", "粘贴链接，让小 D 帮你读") { onUnavailable("链接输入将在后端阶段接入") }
            AttachmentRow(Icons.Outlined.Public, "联网搜索", "搜索并展示信息来源", state.webSearch, onSearch)
            AttachmentRow(Icons.Outlined.Psychology, "深入思考", "复杂问题，多想一会儿", state.deepThinking, onThink)
        }
    }
}

@Composable
private fun AttachmentRow(icon: ImageVector, title: String, subtitle: String, selected: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDiColors.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).quietClick(onClick).padding(horizontal = 7.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) colors.accent else colors.ink, modifier = Modifier.size(40.dp).clip(CircleShape).background(if (selected) colors.peach else colors.soft).padding(10.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
        }
        if (selected) Icon(Icons.Outlined.Check, "已开启", tint = colors.accent)
    }
}
