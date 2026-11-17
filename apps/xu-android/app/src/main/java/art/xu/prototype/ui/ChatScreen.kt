package art.xu.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun ChatScreen(state: DiState, onDrawer: () -> Unit, onNewChat: () -> Unit, onInput: (String) -> Unit, onSend: () -> Unit, onVoice: () -> Unit, onAttach: () -> Unit, onSearch: () -> Unit, onThink: () -> Unit, onPersonality: () -> Unit, onSaveQuote: (String) -> Unit, onRemoveAttachment: () -> Unit, onNotice: (String) -> Unit) {
    val colors = LocalDiColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.activeId) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        DiTopBar(if (state.messages.isEmpty()) "小 D" else state.activeTitle, onDrawer, onNewChat, onPersonality)
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 19.dp)) {
            items(state.messages, key = { it.id }) { message ->
                if (message.fromUser) {
                    Box(Modifier.fillMaxWidth().padding(top = 20.dp, start = 42.dp), contentAlignment = Alignment.CenterEnd) {
                        Surface(shape = RoundedCornerShape(22.dp, 22.dp, 7.dp, 22.dp), color = colors.soft) {
                            Text(message.text, Modifier.padding(horizontal = 17.dp, vertical = 13.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(top = 25.dp)) {
                        if (message.thinking.isNotEmpty()) {
                            Row(Modifier.clip(RoundedCornerShape(9.dp)).background(colors.soft).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                DiMark(Modifier.size(13.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(message.thinking, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
                            }
                            Spacer(Modifier.height(15.dp))
                        }
                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        if (message.sources.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Surface(shape = RoundedCornerShape(15.dp), color = colors.card) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Search, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text("参考了 ${message.sources.size} 个来源 · 演示资料", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    Text("查看", color = colors.accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.quietClick { onNotice("当前为演示来源，真实引用将随后端接入") })
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BookmarkAdd, "收进灵感夹", tint = colors.secondary, modifier = Modifier.size(36.dp).quietClick { onSaveQuote(message.text) }.padding(8.dp))
                            Icon(Icons.Filled.ContentCopy, "复制 · 预览占位", tint = colors.secondary, modifier = Modifier.size(36.dp).quietClick { onNotice("复制功能将在接入真实回答后启用") }.padding(8.dp))
                            Icon(Icons.Filled.PlayArrow, "朗读 · 预览占位", tint = colors.secondary, modifier = Modifier.size(36.dp).quietClick { onNotice("朗读功能尚未接入语音服务") }.padding(8.dp))
                            Icon(Icons.Filled.Refresh, "重新回答 · 预览占位", tint = colors.secondary, modifier = Modifier.size(36.dp).quietClick { onNotice("重新生成将在后端接入后启用") }.padding(8.dp))
                            Spacer(Modifier.weight(1f))
                            Text("演示回答", style = MaterialTheme.typography.labelSmall, color = colors.secondary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            DiComposer(state, onInput, onSend, onVoice, onAttach, onSearch, onThink, onRemoveAttachment)
            Spacer(Modifier.height(13.dp))
        }
    }
}
