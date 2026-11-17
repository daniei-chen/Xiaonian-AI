package art.kaogong.xiaonian.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.data.ConversationDto
import art.kaogong.xiaonian.ui.theme.LocalAccent
import art.kaogong.xiaonian.ui.theme.LocalPalette

/**
 * 侧边抽屉（按真机截图重构）。
 *
 * 结构：标题 + 搜索圆钮 → 六个入口 → 分割线 → 会话列表 → 底部胶囊按钮
 *
 * 六个入口里「资料空间」和「设置」有真实落点，其余是 ChatGPT 的功能，
 * 本地版没有对应实现 —— 点了给明确提示，不做假动作。
 */
@Composable
fun XiaonianDrawer(
    conversations: List<ConversationDto>,
    activeId: String?,
    onSelect: (ConversationDto) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    onNotice: (String) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val shown = remember(conversations, query) {
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title?.contains(query, ignoreCase = true) == true }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = LocalPalette.current.canvas,
        drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "小念",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalPalette.current.ink,
                )
                Spacer(Modifier.weight(1f))
                RoundIcon(
                    icon = if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                    description = if (searching) "关闭搜索" else "搜索会话",
                ) {
                    searching = !searching
                    if (!searching) query = ""
                }
            }

            if (searching) {
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalPalette.current.surfaceMuted),
                    placeholder = { Text("搜索会话内容", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            Spacer(Modifier.height(14.dp))

            // 六个入口（截图顺序）
            MenuRow(Icons.Outlined.AddCircleOutline, "水豚噜噜", badge = true) {
                onNotice("自定义角色还没做，这一项先占位")
            }
            MenuRow(Icons.Outlined.Schedule, "定时任务") {
                onNotice("定时任务需要后台调度，本地版没做")
            }
            MenuRow(Icons.Outlined.FolderOpen, "资料空间") {
                onNotice("资料空间还没做，附件目前在对话里逐条上传")
            }
            MenuRow(Icons.Outlined.Tune, "自定义") { onOpenSettings() }
            MenuRow(Icons.Outlined.Code, "Codex") {
                onNotice("Codex 是 ChatGPT 的代码代理，这里没有")
            }
            MenuRow(Icons.Outlined.Apps, "查看更多") {
                onNotice("没有更多功能了，就这些")
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = LocalPalette.current.divider)
            Spacer(Modifier.height(8.dp))

            if (shown.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "还没有会话" else "没有匹配的会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalPalette.current.inkTertiary,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                shown.forEach { item ->
                    DrawerConversationRow(
                        item = item,
                        selected = item.id == activeId,
                        onClick = { onSelect(item) },
                    )
                }
            }

            HorizontalDivider(color = LocalPalette.current.divider)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 底部胶囊：新对话
                Row(
                    modifier = Modifier
                        .height(46.dp)
                        .clip(CircleShape)
                        .background(LocalAccent.current.accent)
                        .clickable(onClick = onNewChat)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "聊天",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.weight(1f))

                RoundIcon(Icons.Outlined.GraphicEq, "语音对话", onClick = onOpenVoiceMode)

                // 占位头像：本地版没有账号，用设置入口代替
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(LocalPalette.current.surfaceMuted)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "念",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPalette.current.inkSecondary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "导出历史",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalPalette.current.inkTertiary,
                    modifier = Modifier.clickable(onClick = onExport).padding(vertical = 6.dp),
                )
                Text(
                    text = "导入历史",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalPalette.current.inkTertiary,
                    modifier = Modifier.clickable(onClick = onImport).padding(vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalPalette.current.inkTertiary,
                    modifier = Modifier.clickable(onClick = onOpenSettings).padding(vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(LocalPalette.current.surfaceMuted)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = LocalPalette.current.ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalPalette.current.ink,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalPalette.current.ink,
            modifier = Modifier.weight(1f),
        )
        if (badge) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LocalAccent.current.accent),
            )
        }
    }
}

@Composable
private fun DrawerConversationRow(
    item: ConversationDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) {
                    Modifier.background(LocalPalette.current.surfaceMuted)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.title?.takeIf { it.isNotBlank() } ?: "新对话",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) LocalPalette.current.ink else LocalPalette.current.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
