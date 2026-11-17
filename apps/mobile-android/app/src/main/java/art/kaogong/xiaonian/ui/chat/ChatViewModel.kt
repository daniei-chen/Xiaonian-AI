package art.kaogong.xiaonian.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import art.kaogong.xiaonian.data.ChatStreamEvent
import art.kaogong.xiaonian.data.ConversationDto
import art.kaogong.xiaonian.data.MessageDto
import art.kaogong.xiaonian.data.VoiceRecorder
import art.kaogong.xiaonian.data.XiaonianApi
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiCitation(
    val index: Int,
    val url: String,
    val title: String?,
) {
    val host: String
        get() = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
}

data class UiMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String = "",
    val reasoning: String = "",
    val citations: List<UiCitation> = emptyList(),
    val streaming: Boolean = false,
    val failed: Boolean = false,
    val reasoningExpanded: Boolean = false,
)

data class ChatUiState(
    val conversations: List<ConversationDto> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val streaming: Boolean = false,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    /** 联网搜索开关 */
    val webSearch: Boolean = false,
    /** 深入思考开关 */
    val thinking: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    private val api: XiaonianApi = XiaonianApi(),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** 语音输入的转录结果；UI 消费后调 consumeTranscript 清空 */
    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript.asStateFlow()

    private val recorder = VoiceRecorder()
    private var streamJob: Job? = null

    /** 服务端分配的 assistant message id，停止生成时要用 */
    private var remoteAssistantId: String? = null

    init {
        refreshConversations()
    }

    override fun onCleared() {
        runCatching { recorder.stop() }
        super.onCleared()
    }

    fun refreshConversations() {
        viewModelScope.launch {
            runCatching { api.listConversations() }
                .onSuccess { list -> _state.update { it.copy(conversations = list) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun toggleWebSearch() {
        _state.update { it.copy(webSearch = !it.webSearch) }
    }

    fun toggleThinking() {
        _state.update { it.copy(thinking = !it.thinking) }
    }

    // ------------------------------------------------------------ 语音输入

    /** 返回 false 表示录音起不来（通常是没权限或设备不支持） */
    fun startRecording(): Boolean {
        if (_state.value.recording || _state.value.streaming) return false
        val ok = recorder.start()
        if (ok) _state.update { it.copy(recording = true, error = null) }
        return ok
    }

    fun stopRecording() {
        if (!_state.value.recording) return
        val wav = recorder.stop()
        _state.update { it.copy(recording = false) }

        if (wav == null) {
            _state.update { it.copy(error = "录音太短") }
            return
        }

        _state.update { it.copy(transcribing = true) }
        viewModelScope.launch {
            runCatching { api.transcribe(wav) }
                .onSuccess { text ->
                    if (text.isBlank()) {
                        _state.update { it.copy(error = "没听到清晰语音，请再说一次") }
                    } else {
                        _transcript.value = text
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = "语音识别失败：${e.message}") }
                }
            _state.update { it.copy(transcribing = false) }
        }
    }

    fun consumeTranscript() {
        _transcript.value = null
    }

    // ------------------------------------------------------------ 对话

    fun send(text: String) {
        val content = text.trim()
        val snapshot = _state.value
        if (content.isEmpty() || snapshot.streaming) return

        streamJob = viewModelScope.launch {
            val localAssistantId = "a-" + UUID.randomUUID()

            _state.update {
                it.copy(
                    error = null,
                    streaming = true,
                    messages = it.messages +
                        UiMessage(id = "u-" + UUID.randomUUID(), fromUser = true, text = content) +
                        UiMessage(id = localAssistantId, fromUser = false, streaming = true),
                )
            }

            try {
                val conversationId = _state.value.activeConversationId
                    ?: api.createConversation().also { created ->
                        _state.update { it.copy(activeConversationId = created.id) }
                    }.id

                api.streamMessage(
                    conversationId = conversationId,
                    text = content,
                    thinking = snapshot.thinking,
                    webSearch = snapshot.webSearch,
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Accepted -> remoteAssistantId = event.assistantMessageId
                        is ChatStreamEvent.Started -> Unit
                        is ChatStreamEvent.TextDelta ->
                            updateMessage(localAssistantId) { it.copy(text = it.text + event.text) }
                        is ChatStreamEvent.ReasoningDelta ->
                            updateMessage(localAssistantId) {
                                it.copy(reasoning = it.reasoning + event.text)
                            }
                        is ChatStreamEvent.Citations ->
                            updateMessage(localAssistantId) { current ->
                                current.copy(
                                    citations = event.items.map { c ->
                                        UiCitation(index = c.index, url = c.url, title = c.title)
                                    },
                                )
                            }
                        is ChatStreamEvent.Completed ->
                            updateMessage(localAssistantId) { it.copy(streaming = false) }
                        is ChatStreamEvent.Failed ->
                            updateMessage(localAssistantId) {
                                it.copy(
                                    streaming = false,
                                    failed = true,
                                    text = if (it.text.isBlank()) event.message else it.text,
                                )
                            }
                    }
                }

                _state.update { it.copy(streaming = false) }
                refreshConversations()
            } catch (cancel: CancellationException) {
                updateMessage(localAssistantId) { it.copy(streaming = false) }
                _state.update { it.copy(streaming = false) }
                throw cancel
            } catch (e: Exception) {
                updateMessage(localAssistantId) {
                    it.copy(
                        streaming = false,
                        failed = true,
                        text = if (it.text.isBlank()) (e.message ?: "发送失败") else it.text,
                    )
                }
                _state.update { it.copy(streaming = false, error = e.message) }
            } finally {
                remoteAssistantId = null
            }
        }
    }

    fun stop() {
        remoteAssistantId?.let { id ->
            viewModelScope.launch { api.stop(id) }
        }
        streamJob?.cancel()
        streamJob = null
        _state.update { it.copy(streaming = false) }
    }

    fun newConversation() {
        streamJob?.cancel()
        streamJob = null
        remoteAssistantId = null
        _state.update {
            it.copy(
                activeConversationId = null,
                messages = emptyList(),
                streaming = false,
                error = null,
            )
        }
    }

    fun openConversation(id: String) {
        if (_state.value.streaming) return
        viewModelScope.launch {
            runCatching { api.loadConversation(id) }
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            activeConversationId = id,
                            messages = detail.messages.map(::toUiMessage),
                            streaming = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun toggleReasoning(id: String) {
        updateMessage(id) { it.copy(reasoningExpanded = !it.reasoningExpanded) }
    }

    private fun updateMessage(id: String, transform: (UiMessage) -> UiMessage) {
        _state.update { current ->
            current.copy(messages = current.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun toUiMessage(dto: MessageDto): UiMessage {
        val citations = dto.blocks
            .firstOrNull { it.type == "text" }
            ?.citations
            ?.map { UiCitation(index = it.index, url = it.url, title = it.title) }
            .orEmpty()

        return UiMessage(
            id = dto.id,
            fromUser = dto.role == "user",
            text = dto.blocks.filter { it.type == "text" }.mapNotNull { it.text }.joinToString(""),
            reasoning = dto.blocks.filter { it.type == "reasoning" }.mapNotNull { it.text }.joinToString(""),
            citations = citations,
            streaming = dto.status == "streaming",
            failed = dto.status == "failed",
        )
    }
}
