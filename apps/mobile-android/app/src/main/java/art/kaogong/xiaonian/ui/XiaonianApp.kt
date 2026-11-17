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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import art.kaogong.xiaonian.ui.chat.ChatScreen
import art.kaogong.xiaonian.ui.chat.ChatViewModel
import art.kaogong.xiaonian.ui.drawer.XiaonianDrawer
import art.kaogong.xiaonian.ui.model.ChatMode
import art.kaogong.xiaonian.ui.voice.VoiceModeScreen
import kotlinx.coroutines.launch

@Composable
fun XiaonianApp(
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(ChatMode.Chat) }
    var input by remember { mutableStateOf("") }
    /** 全屏语音对话模式 */
    var voiceMode by remember { mutableStateOf(false) }

    if (voiceMode) {
        VoiceModeScreen(
            onClose = { voiceMode = false },
            onOpenDrawer = { scope.launch { drawerState.open() } },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color(0x33000000),
        drawerContent = {
            XiaonianDrawer(
                conversations = state.conversations,
                activeId = state.activeConversationId,
                onSelect = { selected ->
                    viewModel.openConversation(selected.id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.newConversation()
                    input = ""
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        ChatScreen(
            state = state,
            mode = mode,
            onModeChange = { mode = it },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewChat = {
                viewModel.newConversation()
                input = ""
            },
            onConversationClick = { id -> viewModel.openConversation(id) },
            input = input,
            onInputChange = { input = it },
            onSend = {
                viewModel.send(input)
                input = ""
            },
            onStop = { viewModel.stop() },
            // 框内麦克风：语音输入（ASR 转文字进输入框）—— WP10
            onVoiceInput = {},
            // 框外紫色圆钮：进入全屏语音对话
            onOpenVoiceMode = { voiceMode = true },
            onAttach = {},
            onToggleReasoning = { id -> viewModel.toggleReasoning(id) },
        )
    }
}
