package art.kaogong.xiaonian.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import art.kaogong.xiaonian.data.ChatStreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import art.kaogong.xiaonian.data.ConversationDto
import art.kaogong.xiaonian.data.FileRef
import art.kaogong.xiaonian.data.MessageDto
import art.kaogong.xiaonian.data.Settings
import art.kaogong.xiaonian.data.VoiceRecorder
import art.kaogong.xiaonian.data.XiaonianApi
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** 已上传、等待随消息发出的附件 */
data class PendingAttachment(
    val fileId: String,
    val name: String,
    val mime: String,
    val size: Long,
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

data class ChatUiState(
    val conversations: List<ConversationDto> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<UiMessage> = emptyList(),
    val attachments: List<PendingAttachment> = emptyList(),
    val uploading: Boolean = false,
    val streaming: Boolean = false,
    val recording: Boolean = false,
    val transcribing: Boolean = false,
    /** 联网搜索开关（透传上游 server-tools） */
    val webSearch: Boolean = false,
    /** 深度搜索开关（能力层自建 loop） */
    val deepSearch: Boolean = false,
    /** 深入思考开关 */
    val thinking: Boolean = false,
    /** 思考强度：high 快，max 深 */
    val effort: String = "high",
    /** 正在从本地库读某个会话 */
    val loadingConversation: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    /** 全本地：会话与消息在本机 SQLite，聊天直连上游 */
    private val store = art.kaogong.xiaonian.data.local.LocalStore(app)
    private val chatApi = art.kaogong.xiaonian.data.DirectChatApi()
    private val tools = art.kaogong.xiaonian.data.DirectToolsApi {
        Settings.apiKey(app, Settings.PROVIDER_DEEPSEEK)
    }
    private val repo = art.kaogong.xiaonian.data.local.ChatRepository(app, store, chatApi, tools)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** 语音输入的转录结果；UI 消费后调 consumeTranscript 清空 */
    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript.asStateFlow()

    private val recorder = VoiceRecorder()
    private var streamJob: Job? = null

    /** 语音对话模式的回答音频；UI 收集后播放 */
    private val _voiceAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
    val voiceAudio: SharedFlow<ByteArray> = _voiceAudio.asSharedFlow()

    /** 语音对话页是否在前台，决定回答要不要自动朗读 */
    var voiceModeActive: Boolean = false

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
            runCatching { repo.conversations() }
                .onSuccess { list -> _state.update { it.copy(conversations = list) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun toggleWebSearch() {
        _state.update { it.copy(webSearch = !it.webSearch, deepSearch = false) }
    }

    fun toggleDeepSearch() {
        _state.update { it.copy(deepSearch = !it.deepSearch, webSearch = false) }
    }

    fun toggleThinking() {
        _state.update { it.copy(thinking = !it.thinking) }
    }

    fun setEffort(value: String) {
        _state.update { it.copy(effort = value) }
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** 设置保存后把默认思考/强度应用到当前会话 */
    fun applyDefaults(thinking: Boolean, effort: String) {
        _state.update { it.copy(thinking = thinking, effort = effort) }
    }

    /** 设置里的「清除所有对话」：真的删库 */
    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearAll() }
            _state.update {
                it.copy(
                    messages = emptyList(),
                    activeConversationId = null,
                    conversations = emptyList(),
                    error = "已清除全部对话",
                )
            }
            refreshConversations()
        }
    }

    /** 设置页用：验证密钥是否能通（只读请求，不消耗额度） */
    fun testUpstream(
        provider: String,
        apiKey: String,
        model: String,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch {
            val (ok, message) = chatApi.verify(provider, Settings.baseUrl(provider), apiKey)
            onResult(ok, message)
        }
    }

    /** 读链接：正文走 transcript 通道填进输入框，用户可以先看一眼再发 */
    fun fetchLink(url: String) {
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.tool.fetchPage(url).second }
                .onSuccess { text ->
                    if (text.isBlank()) {
                        _state.update { it.copy(error = "这个链接没有取到正文") }
                    } else {
                        // 太长的正文塞进输入框没法看，截断并注明
                        val clipped = if (text.length > 6000) text.take(6000) + "\n\n…（正文过长已截断）" else text
                        _transcript.value = "$url\n\n$clipped"
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = "读取失败：${e.message}") } }
            _state.update { it.copy(uploading = false) }
        }
    }

    /** 图片转文字：结果走 transcript 通道，直接填进输入框 */
    fun runOcr(bytes: ByteArray, name: String, mime: String) {
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.tool.ocr(bytes, name, mime) }
                .onSuccess { text ->
                    if (text.isBlank()) {
                        _state.update { it.copy(error = "这张图里没识别到文字") }
                    } else {
                        _transcript.value = text
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = "OCR 失败：${e.message}") } }
            _state.update { it.copy(uploading = false) }
        }
    }

    // ------------------------------------------------------------ 附件

    fun uploadAttachment(bytes: ByteArray, name: String, mime: String) {
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.tool.upload(bytes, name, mime) }
                .onSuccess { f ->
                    _state.update { s ->
                        s.copy(
                            attachments = s.attachments +
                                PendingAttachment(f.fileId, f.name, f.mime, f.size),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = "上传失败：${e.message}") } }
            _state.update { it.copy(uploading = false) }
        }
    }

    fun removeAttachment(fileId: String) {
        _state.update { s -> s.copy(attachments = s.attachments.filterNot { it.fileId == fileId }) }
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
            runCatching { repo.tool.transcribe(wav, "recording.wav", "audio/wav") }
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

    /**
     * 语音对话：结束这一轮录音并自动提问。
     * 回答会被自动朗读（见 send 结尾的 speak）。
     */
    fun finishVoiceTurn() {
        if (!_state.value.recording) return
        val wav = recorder.stop()
        _state.update { it.copy(recording = false) }

        if (wav == null) {
            _state.update { it.copy(error = "录音太短") }
            return
        }

        _state.update { it.copy(transcribing = true) }
        viewModelScope.launch {
            val text = runCatching { repo.tool.transcribe(wav, "recording.wav", "audio/wav") }
                .getOrNull().orEmpty()
            _state.update { it.copy(transcribing = false) }

            if (text.isBlank()) {
                _state.update { it.copy(error = "没听到清晰语音，请再说一次") }
                return@launch
            }

            _transcript.value = text
            send(text)
        }
    }

    /** 把文本合成语音推给 UI 播放；消息上的「朗读」也走这里 */
    fun speakMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val voice = Settings.ttsVoice(getApplication())
            // 按句合成、逐句推送：第一句出来就能播，其余在旁边合成
            repo.tool.synthesizeBySentence(trimmed.take(2000), voice) { _, audio ->
                _voiceAudio.emit(audio)
            }
        }
    }

    // ------------------------------------------------------------ 对话

    fun send(
        text: String,
        replayFromMessageId: String? = null,
        continueGeneration: Boolean = false,
        /** 本地也要先裁掉这些，UI 才不会闪一下旧消息 */
        trimAfterLocalId: String? = null,
        /** 重新生成：先从本地库删掉最后一条回答 */
        dropLastAssistant: Boolean = false,
    ) {
        val content = text.trim()
        val snapshot = _state.value
        if (snapshot.streaming) return
        // 允许只发附件不写文字；继续生成则不需要文字
        if (content.isEmpty() && snapshot.attachments.isEmpty() && !continueGeneration) return

        val files = snapshot.attachments.map {
            FileRef(
                fileId = it.fileId,
                kind = if (it.isImage) "image" else "document",
                name = it.name,
                mime = it.mime,
                size = it.size,
            )
        }

        streamJob = viewModelScope.launch {
            val localAssistantId = "a-" + UUID.randomUUID()

            _state.update { current ->
                var base = current.messages
                if (trimAfterLocalId != null) {
                    val idx = base.indexOfFirst { it.id == trimAfterLocalId }
                    if (idx >= 0) base = base.take(idx)
                }

                val added = mutableListOf<UiMessage>()
                if (!continueGeneration) {
                    added += UiMessage(
                        id = "u-" + UUID.randomUUID(),
                        fromUser = true,
                        text = content,
                    )
                }
                added += UiMessage(id = localAssistantId, fromUser = false, streaming = true)

                current.copy(
                    error = null,
                    streaming = true,
                    messages = base + added,
                )
            }

            try {
                val conversationId = _state.value.activeConversationId
                    ?: repo.createConversation().also { created ->
                        _state.update { it.copy(activeConversationId = created) }
                    }

                if (dropLastAssistant) {
                    withContext(Dispatchers.IO) { repo.dropLastTurn(conversationId) }
                }

                val continueFrom = if (continueGeneration) {
                    withContext(Dispatchers.IO) { repo.lastAssistant(conversationId) }
                } else {
                    null
                }

                repo.send(
                    conversationId = conversationId,
                    text = content,
                    thinking = snapshot.thinking,
                    effort = snapshot.effort,
                    files = files,
                    webSearch = snapshot.webSearch,
                    deepSearch = snapshot.deepSearch,
                    continueFrom = continueFrom,
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Accepted -> remoteAssistantId = event.assistantMessageId
                        is ChatStreamEvent.Started -> Unit
                        is ChatStreamEvent.BlockStart -> Unit
                        is ChatStreamEvent.Signature -> Unit
                        is ChatStreamEvent.Usage -> Unit
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
                        is ChatStreamEvent.Failed -> {
                            android.util.Log.e("ChatViewModel", "stream failed: ${event.code} / ${event.message}")
                            updateMessage(localAssistantId) {
                                it.copy(streaming = false, failed = true)
                            }
                            // 把具体原因抬到界面上：只显示「生成失败」等于什么都没说
                            _state.update { it.copy(error = event.message) }
                        }
                    }
                }

                _state.update { it.copy(streaming = false, attachments = emptyList()) }
                refreshConversations()

                // 语音对话模式：回答落地后自动朗读
                if (voiceModeActive) {
                    _state.value.messages
                        .lastOrNull { !it.fromUser }
                        ?.text
                        ?.takeIf { it.isNotBlank() }
                        ?.let { speakMessage(it) }
                }
            } catch (cancel: CancellationException) {
                updateMessage(localAssistantId) { it.copy(streaming = false) }
                _state.update { it.copy(streaming = false) }
                throw cancel
            } catch (e: Exception) {
                updateMessage(localAssistantId) { it.copy(streaming = false, failed = true) }
                _state.update { it.copy(streaming = false, error = "发送失败：${e.message ?: "未知错误"}") }
            } finally {
                remoteAssistantId = null
            }
        }
    }

    /** 重新生成某条回答：找到它前面那条用户消息，从那里重放 */
    fun regenerate(assistantId: String) {
        val messages = _state.value.messages
        val idx = messages.indexOfFirst { it.id == assistantId }
        if (idx <= 0) return
        val userMessage = messages.take(idx).lastOrNull { it.fromUser } ?: return
        send(
            text = userMessage.text,
            trimAfterLocalId = userMessage.id,
            dropLastAssistant = true,
        )
    }

    /** 导出结果：UI 收到后弹系统保存框 */
    private val _exportBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
    val exportBytes: SharedFlow<ByteArray> = _exportBytes.asSharedFlow()

    fun exportHistory() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    art.kaogong.xiaonian.data.local.HistoryArchive.export(store)
                }
            }
                .onSuccess { bytes -> if (bytes.isNotEmpty()) _exportBytes.emit(bytes) }
                .onFailure { e ->
                    _state.update { it.copy(error = "导出失败：${e.message}") }
                }
        }
    }

    fun importHistory(bytes: ByteArray) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    art.kaogong.xiaonian.data.local.HistoryArchive.import(store, bytes)
                }
            }
                .onSuccess { (conversations, messages) ->
                    _state.update {
                        it.copy(error = "已导入 $conversations 个会话、$messages 条消息")
                    }
                    refreshConversations()
                }
                .onFailure { e ->
                    _state.update { it.copy(error = "导入失败：${e.message}") }
                }
        }
    }

    /** 接着最后一条回答继续写 */
    fun continueGeneration() {
        if (_state.value.messages.none { !it.fromUser && it.text.isNotBlank() }) return
        send(text = "", continueGeneration = true)
    }

    fun stop() {
        // 本地模式下"停止"就是取消协程，没有服务端任务要收
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
        _state.update { it.copy(loadingConversation = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.messages(id) }
                .onSuccess { messages ->
                    _state.update {
                        it.copy(
                            activeConversationId = id,
                            messages = messages.map(::toUiMessage),
                            streaming = false,
                            loadingConversation = false,
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
