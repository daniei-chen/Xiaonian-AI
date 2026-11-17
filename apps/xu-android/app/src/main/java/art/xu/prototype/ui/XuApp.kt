package art.xu.prototype.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.XuTheme

@Composable
fun XuApp() {
    var state by remember { mutableStateOf(DiState()) }
    val context = LocalContext.current
    androidx.compose.runtime.SideEffect {
        val controller = androidx.core.view.WindowInsetsControllerCompat((context as android.app.Activity).window, (context as android.app.Activity).window.decorView)
        if (state.referenceHome) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    androidx.compose.runtime.LaunchedEffect(state.drawerOpen) {
        if (state.drawerOpen) drawerState.open() else drawerState.close()
    }
    val newChat: () -> Unit = { state = state.copy(activeId = null, referenceHome = false, drawerOpen = false, voiceOpen = false, settingsOpen = false, libraryOpen = false, input = "", attachmentName = null) }
    val openChat: (Int) -> Unit = { id -> state = state.copy(activeId = id, drawerOpen = false, voiceOpen = false, settingsOpen = false, libraryOpen = false) }
    val toggleSearch: () -> Unit = { state = state.copy(webSearch = !state.webSearch) }
    val toggleThink: () -> Unit = { state = state.copy(deepThinking = !state.deepThinking) }
    val send: () -> Unit = {
        val text = state.input.trim()
        if (text.isBlank() && state.attachmentName == null) {
            Toast.makeText(context, "想说点什么？写一句就好。", Toast.LENGTH_SHORT).show()
        } else {
            val content = text.ifBlank { "请和我一起看看这个文件" }
            val id = state.nextMessageId
            val activeId = state.activeId ?: state.nextConversationId
            val user = ChatMessage(id, true, if (state.attachmentName != null) "$content\n📎 ${state.attachmentName}" else content)
            val responseText = if (text.contains("周末")) {
                "那就把“不赶路”当成唯一的安排。\n\n上午睡到自然醒，找一家不用排队的店吃早餐；下午去一条没走过的街散散步，看到喜欢的小店就进去坐坐。\n\n晚上留给你最想做的一件小事：看一部电影、做一顿饭，或者什么都不做。周末不需要被充分利用，被你好好感受就够了。"
            } else if (text.contains("改") || text.contains("文字")) {
                "可以，把原文发给我就好。\n\n我会尽量保留你的语气，只把拗口的地方理顺，不把它改成千篇一律的“标准答案”。如果你有特别想保留的词，也可以一起告诉我。"
            } else {
                "我们不用急着找到最好的答案，可以先从你真正关心的那一点开始。\n\n如果把它想成一件很小、今天就能做的事，你会愿意先试试哪一步？也可以只是说说，为什么这件事会出现在你的脑海里。"
            }
            val answer = ChatMessage(id + 1, false, responseText, if (state.webSearch) listOf("演示来源 1", "演示来源 2", "演示来源 3") else emptyList(), if (state.deepThinking) "认真想了一会儿 · 演示过程" else "")
            val old = state.conversations.find { it.id == activeId }
            val conversation = Conversation(activeId, old?.title ?: content.take(26), messages = (old?.messages.orEmpty()) + user + answer)
            state = state.copy(
                activeId = activeId,
                conversations = listOf(conversation) + state.conversations.filterNot { it.id == activeId },
                nextConversationId = if (state.activeId == null) state.nextConversationId + 1 else state.nextConversationId,
                nextMessageId = id + 2,
                input = "",
                attachmentName = null,
                drawerOpen = false,
            )
        }
    }
    XuTheme(darkTheme = state.dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(if (state.referenceHome) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    androidx.compose.material3.Surface(Modifier.width(322.dp).fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        DrawerScreen(state, onClose = { state = state.copy(drawerOpen = false) }, onNew = newChat, onConversation = openChat, onSearch = { state = state.copy(searchQuery = it) }, onSettings = { state = state.copy(settingsOpen = true) }, onLibrary = { state = state.copy(libraryOpen = true) }, onPersonality = { state = state.copy(personalityOpen = true) })
                    }
                },
            ) {
                when {
                    state.settingsOpen -> ProfileScreen(state, onBack = { state = state.copy(settingsOpen = false) }, onDark = { state = state.copy(dark = !state.dark) }, onReducedMotion = { state = state.copy(reducedMotion = !state.reducedMotion) }, onPersonality = { state = state.copy(personalityOpen = true) })
                    state.libraryOpen -> LibraryScreen(state, onBack = { state = state.copy(libraryOpen = false) }, onContinue = { text ->
                        newChat()
                        state = state.copy(input = "我想接着聊聊这句话：\n$text")
                    })
                    state.activeId != null -> ChatScreen(state, onDrawer = { state = state.copy(drawerOpen = true) }, onNewChat = newChat, onInput = { state = state.copy(input = it) }, onSend = send, onVoice = { state = state.copy(voiceOpen = true) }, onAttach = { state = state.copy(attachmentOpen = true) }, onSearch = toggleSearch, onThink = toggleThink, onPersonality = { state = state.copy(personalityOpen = true) }, onSaveQuote = { text ->
                        state = state.copy(savedQuotes = state.savedQuotes + SavedQuote(text, state.activeTitle), notice = "已收进灵感夹")
                    }, onRemoveAttachment = { state = state.copy(attachmentName = null) }, onNotice = { state = state.copy(notice = it) })
                    else -> if (state.referenceHome) ReferenceHomeScreen(
                        state = state,
                        onDrawer = { state = state.copy(drawerOpen = true) },
                        onNewChat = newChat,
                        onInput = { state = state.copy(input = it) },
                        onSend = send,
                        onVoice = { state = state.copy(voiceOpen = true) },
                        onAttach = { state = state.copy(attachmentOpen = true) },
                        onRecent = { state = state.copy(referenceHome = false); openChat(it) },
                        onWork = { state = state.copy(personalityOpen = true) },
                    ) else HomeScreen(state, onDrawer = { state = state.copy(drawerOpen = true) }, onNewChat = newChat, onInput = { state = state.copy(input = it) }, onSend = send, onVoice = { state = state.copy(voiceOpen = true) }, onAttach = { state = state.copy(attachmentOpen = true) }, onSearch = toggleSearch, onThink = toggleThink, onPersonality = { state = state.copy(personalityOpen = true) }, onRecent = openChat)
                }
            }
            if (state.voiceOpen) VoiceScreen(state, onToggle = { state = state.copy(listening = !state.listening) }, onClose = { state = state.copy(voiceOpen = false, listening = false) })
            state.notice?.let {
                Box(Modifier.align(Alignment.BottomCenter)) { DiSnackbar(it, onDismiss = { state = state.copy(notice = null) }) }
            }
        }
        if (state.attachmentOpen) AttachmentSheet(state, onDismiss = { state = state.copy(attachmentOpen = false) }, onSearch = toggleSearch, onThink = toggleThink, onFile = { state = state.copy(attachmentName = it, attachmentOpen = false, notice = "已添加演示附件，未上传任何文件") }, onUnavailable = { state = state.copy(notice = it, attachmentOpen = false) })
        if (state.personalityOpen) PersonalitySheet(state.personality, onSelect = { state = state.copy(personality = it, personalityOpen = false, notice = "已选择${it}语气，将在接入后生效") }, onDismiss = { state = state.copy(personalityOpen = false) })
    }
    BackHandler(enabled = state.voiceOpen || state.drawerOpen || state.settingsOpen || state.libraryOpen || state.activeId != null || !state.referenceHome) {
        state = when {
            state.voiceOpen -> state.copy(voiceOpen = false, listening = false)
            drawerState.isOpen -> state.copy(drawerOpen = false)
            state.settingsOpen -> state.copy(settingsOpen = false)
            state.libraryOpen -> state.copy(libraryOpen = false)
            else -> state.copy(activeId = null, referenceHome = true)
        }
    }
}
