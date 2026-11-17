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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun HomeScreen(state: DiState, onDrawer: () -> Unit, onNewChat: () -> Unit, onInput: (String) -> Unit, onSend: () -> Unit, onVoice: () -> Unit, onAttach: () -> Unit, onSearch: () -> Unit, onThink: () -> Unit, onPersonality: () -> Unit, onRecent: (Int) -> Unit) {
    val colors = LocalDiColors.current
    Column(Modifier.fillMaxSize().imePadding()) {
        DiTopBar("小 D", onDrawer, onNewChat, onPersonality)
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 16.dp)) {
            state.conversations.take(3).forEach { conversation ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).quietClick { onRecent(conversation.id) }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(colors.soft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = colors.secondary, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Text(conversation.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        DiComposer(state, onInput, onSend, onVoice, onAttach, onSearch, onThink, {})
        Spacer(Modifier.height(10.dp))
    }
}
