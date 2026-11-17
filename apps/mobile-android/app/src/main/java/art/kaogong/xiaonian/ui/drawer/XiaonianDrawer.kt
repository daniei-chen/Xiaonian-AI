package art.kaogong.xiaonian.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.data.ConversationDto
import art.kaogong.xiaonian.ui.components.CircleIconButton
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 左侧抽屉。结构与截图一致：标题 + 功能区 → 分隔线 → 会话历史 → 底部主操作。
 * P0 里功能项先不跳转，重点是把版式与层级做对。
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
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // 空查询显示全部；否则按标题过滤（消息内容搜索需要 FTS，后面再说）
    val shown = remember(conversations, query) {
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title?.contains(query, ignoreCase = true) == true }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = XiaonianColors.Canvas,
        drawerShape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(26.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "小念",
                    style = MaterialTheme.typography.headlineMedium,
                    color = XiaonianColors.Ink,
                )
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    icon = if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                    contentDescription = if (searching) "关闭搜索" else "搜索会话",
                    onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    },
                )
            }

            // 搜索展开后才占位，平时不挤列表
            if (searching) {
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(XiaonianColors.SurfaceMuted),
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

            Spacer(Modifier.height(18.dp))

            DrawerMenuRow(Icons.Outlined.Settings, "设置", onClick = onOpenSettings)

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = XiaonianColors.Divider)
            Spacer(Modifier.height(4.dp))

            // 放在会话列表之前：列表很长时贴底的按钮会被挤出可视区
            DrawerAction("导出历史", onExport)
            DrawerAction("导入历史", onImport)

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = XiaonianColors.Divider)
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty() && query.isNotBlank()) {
                Text(
                    text = "没有匹配的会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaonianColors.InkTertiary,
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
                )
            }

            shown.forEach { item ->
                DrawerConversationRow(
                    item = item,
                    selected = item.id == activeId,
                    onClick = { onSelect(item) },
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(XiaonianColors.Accent)
                        .clickable(onClick = onNewChat)
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "新对话",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(XiaonianColors.SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "念",
                        style = MaterialTheme.typography.labelLarge,
                        color = XiaonianColors.InkSecondary,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

/** 抽屉底部的次级操作：低饱和、无图标，避免和会话列表抢视线 */
@Composable
private fun DrawerAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = XiaonianColors.InkSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun DrawerMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = XiaonianColors.Ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = XiaonianColors.Ink,
        )
    }
}

@Composable
private fun DrawerConversationRow(
    item: ConversationDto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = item.title ?: "新对话",
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) XiaonianColors.Ink else XiaonianColors.InkSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
    )
}
