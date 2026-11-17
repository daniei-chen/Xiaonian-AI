package art.kaogong.xiaonian.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.model.ChatMode
import art.kaogong.xiaonian.ui.model.ConversationSummary
import art.kaogong.xiaonian.ui.theme.XiaonianColors

@Composable
fun ChatScreen(
    mode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    conversations: List<ConversationSummary>,
    onConversationClick: (ConversationSummary) -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttach: () -> Unit,
) {
    Scaffold(
        containerColor = XiaonianColors.Canvas,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ChatTopBar(
                mode = mode,
                onModeChange = onModeChange,
                onOpenDrawer = onOpenDrawer,
                onNewChat = onNewChat,
                // 顶栏要自己吃状态栏，Scaffold 的 contentWindowInsets 管不到 topBar
                modifier = Modifier.statusBarsPadding(),
            )
        },
        bottomBar = {
            Composer(
                value = input,
                onValueChange = onInputChange,
                onSend = onSend,
                onVoice = onVoice,
                onAttach = onAttach,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // 新会话页：上方留白，最近会话贴着输入框往上排（与截图一致）
            Spacer(Modifier.weight(1f))

            conversations.forEach { item ->
                RecentConversationRow(item = item, onClick = onConversationClick)
            }
        }
    }
}

@Composable
private fun RecentConversationRow(
    item: ConversationSummary,
    onClick: (ConversationSummary) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(item) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(XiaonianColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = XiaonianColors.InkSecondary,
                modifier = Modifier.size(14.dp),
            )
        }

        Spacer(Modifier.width(13.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = XiaonianColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(Modifier.height(2.dp))
}
