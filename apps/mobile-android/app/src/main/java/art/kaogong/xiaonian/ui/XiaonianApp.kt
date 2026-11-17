package art.kaogong.xiaonian.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var mode by remember { mutableStateOf(ChatMode.Chat) }
    var input by remember { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(false) }
    /** 刚申请权限、授权后要立刻开始录音 */
    var pendingRecord by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingRecord) {
            pendingRecord = false
            viewModel.startRecording()
        } else {
            pendingRecord = false
        }
    }

    // 转录回来填进输入框，不自动发送
    LaunchedEffect(transcript) {
        transcript?.let { text ->
            input = if (input.isBlank()) text else "$input $text"
            viewModel.consumeTranscript()
        }
    }

    val onVoiceInput: () -> Unit = {
        if (state.recording) {
            viewModel.stopRecording()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                viewModel.startRecording()
            } else {
                pendingRecord = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

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
            onVoiceInput = onVoiceInput,
            onOpenVoiceMode = { voiceMode = true },
            onAttach = {},
            onToggleReasoning = { id -> viewModel.toggleReasoning(id) },
        )
    }
}
