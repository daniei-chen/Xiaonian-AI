package art.kaogong.xiaonian.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import art.kaogong.xiaonian.ui.chat.ChatScreen
import art.kaogong.xiaonian.ui.drawer.XiaonianDrawer
import art.kaogong.xiaonian.ui.model.ChatMode
import art.kaogong.xiaonian.ui.model.ConversationSummary
import art.kaogong.xiaonian.ui.model.sampleConversations
import kotlinx.coroutines.launch

@Composable
fun XiaonianApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(ChatMode.Chat) }
    var input by remember { mutableStateOf("") }
    var active by remember { mutableStateOf<ConversationSummary?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color(0x33000000),
        drawerContent = {
            XiaonianDrawer(
                conversations = sampleConversations,
                activeId = active?.id,
                onSelect = { selected ->
                    active = selected
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    active = null
                    input = ""
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        ChatScreen(
            mode = mode,
            onModeChange = { mode = it },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewChat = {
                active = null
                input = ""
            },
            conversations = sampleConversations,
            onConversationClick = { active = it },
            input = input,
            onInputChange = { input = it },
            onSend = { input = "" },
            onVoice = {},
            onAttach = {},
        )
    }
}
