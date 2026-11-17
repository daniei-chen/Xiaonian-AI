package art.xu.prototype.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun DrawerScreen(state: DiState, onClose: () -> Unit, onNew: () -> Unit, onConversation: (Int) -> Unit, onSearch: (String) -> Unit, onSettings: () -> Unit, onLibrary: () -> Unit, onPersonality: () -> Unit) {
    val colors = LocalDiColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().height(75.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(10.dp))
            Text("小 D", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClose) { Icon(Icons.Outlined.Close, "关闭菜单", tint = colors.ink) }
        }
        Surface(shape = RoundedCornerShape(13.dp), color = colors.soft) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, "搜索会话", tint = colors.secondary, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(value = state.searchQuery, onValueChange = onSearch, singleLine = true, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink), decorationBox = { inner ->
                    Box {
                        if (state.searchQuery.isEmpty()) Text("搜索对话", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
                        inner()
                    }
                })
            }
        }
        Spacer(Modifier.height(18.dp))
        DrawerItem(Icons.Outlined.Edit, "新对话", onNew)
        DrawerItem(Icons.Outlined.BookmarkBorder, "灵感夹", onLibrary, "${state.savedQuotes.size}")
        DrawerItem(Icons.Outlined.AutoAwesome, "小 D 的性格", onPersonality, state.personality)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Spacer(Modifier.height(16.dp))
        val filtered = state.conversations.filter { it.title.contains(state.searchQuery, ignoreCase = true) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            listOf("今天", "过去 7 天", "过去 30 天").forEach { group ->
                val items = filtered.filter { it.group == group }
                if (items.isNotEmpty()) {
                    Text(group, style = MaterialTheme.typography.labelSmall, color = colors.secondary, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                    items.forEach { conversation ->
                        Text(conversation.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = if (state.activeId == conversation.id) colors.accent else colors.ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (state.activeId == conversation.id) colors.peach else colors.canvas).quietClick { onConversation(conversation.id) }.padding(horizontal = 12.dp, vertical = 12.dp))
                    }
                }
            }
            if (filtered.isEmpty()) Text("没有找到相关对话", style = MaterialTheme.typography.bodyMedium, color = colors.secondary, modifier = Modifier.padding(18.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).quietClick(onSettings).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(37.dp).clip(CircleShape).background(colors.peach), contentAlignment = Alignment.Center) { Text("D", color = colors.accent, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("我的 小 D", style = MaterialTheme.typography.labelLarge)
                Text("偏好与界面设置", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
            }
            Icon(Icons.Outlined.Settings, "设置", tint = colors.secondary, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(17.dp))
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit, detail: String? = null) {
    val colors = LocalDiColors.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).quietClick(onClick).padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.ink, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
    }
}
