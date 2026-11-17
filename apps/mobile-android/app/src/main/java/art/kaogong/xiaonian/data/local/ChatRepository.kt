package art.kaogong.xiaonian.data.local

import android.content.Context
import art.kaogong.xiaonian.data.BlockDto
import art.kaogong.xiaonian.data.ChatStreamEvent
import art.kaogong.xiaonian.data.CitationDto
import art.kaogong.xiaonian.data.DirectChatApi
import art.kaogong.xiaonian.data.DirectToolsApi
import art.kaogong.xiaonian.data.FileRef
import art.kaogong.xiaonian.data.MessageDto
import art.kaogong.xiaonian.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 本地仓库：会话与消息全在本机 SQLite，聊天直连上游。
 *
 * 深度搜索在这里实现：搜 -> 抓正文 -> 编号注入 system prompt。
 * 和 Gateway 版本逻辑一致，只是改在设备上跑。
 */
class ChatRepository(
    private val context: Context,
    private val store: LocalStore,
    private val chatApi: DirectChatApi,
    /** 工具能力：搜索/OCR/语音/上传，ViewModel 直接调用 */
    val tool: DirectToolsApi,
) {

    fun conversations() = store.listConversations()

    fun conversation(id: String) = store.getConversation(id)
    fun messages(conversationId: String) = store.listMessages(conversationId)

    fun createConversation(): String = store.createConversation().id

    fun deleteMessagesFrom(conversationId: String, messageId: String) {
        val seq = store.messageSeq(messageId) ?: return
        store.deleteMessagesAfter(conversationId, seq)
    }

    /**
     * 重新生成：删掉最后一轮（用户消息 + 回答），随后 send 会重新写入。
     * UI 侧的 id 是本地生成的，和库里不一致，所以按"最后一轮"定位而不是按 id。
     */
    fun dropLastTurn(conversationId: String) {
        val lastUser = store.listMessages(conversationId).lastOrNull { it.role == "user" } ?: return
        store.deleteMessagesAfter(conversationId, lastUser.seq)
    }

    /** 续写锚点：最后一条有内容的回答 */
    fun lastAssistant(conversationId: String) =
        store.listMessages(conversationId).lastOrNull { it.role == "assistant" }

    /**
     * 发一条消息。user 与 assistant 两条记录都会落到本地，
     * assistant 在流式过程中反复更新。
     */
    fun send(
        conversationId: String,
        text: String,
        thinking: Boolean,
        effort: String,
        files: List<FileRef>,
        webSearch: Boolean,
        deepSearch: Boolean,
        continueFrom: MessageDto? = null,
    ): Flow<ChatStreamEvent> = flow {
        val provider = Settings.provider(context)
        val apiKey = Settings.apiKey(context, provider)
        val model = Settings.model(context)
        val baseUrl = Settings.baseUrl(provider)

        if (apiKey.isBlank()) {
            emit(ChatStreamEvent.Failed("no_key", "还没配置密钥：菜单 → 设置"))
            return@flow
        }

        val history = withContext(Dispatchers.IO) { store.listMessages(conversationId) }

        // 续写不产生用户消息，提示词只发给上游
        if (continueFrom == null) {
            withContext(Dispatchers.IO) {
                store.appendMessage(
                    conversationId = conversationId,
                    role = "user",
                    blocks = buildList {
                        if (text.isNotBlank()) add(BlockDto(type = "text", text = text))
                    },
                )
                if (store.getConversation(conversationId)?.title.isNullOrBlank() && text.isNotBlank()) {
                    store.setTitleIfEmpty(conversationId, text.take(24))
                }
            }
        }

        // 检索：搜 + （深度模式才抓正文）+ 编号注入
        var system: String? = null
        val preCitations = mutableListOf<CitationDto>()

        if ((webSearch || deepSearch) && text.isNotBlank()) {
            val deep = deepSearch
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val hits = tool.search(text, if (deep) 8 else 6)
                    val lines = StringBuilder()
                    hits.forEachIndexed { index, hit ->
                        val body = if (deep && index < 3) {
                            runCatching { tool.fetchPage(hit.url).second.take(3000) }
                                .getOrDefault(hit.snippet)
                        } else {
                            hit.snippet
                        }
                        lines.append("### [${index + 1}] ${hit.title}\nURL: ${hit.url}\n\n$body\n\n")
                        preCitations.add(
                            CitationDto(index = index + 1, url = hit.url, title = hit.title),
                        )
                    }
                    lines.toString() to preCitations.toList()
                }
            }.getOrNull()

            if (outcome != null && outcome.second.isNotEmpty()) {
                system = buildString {
                    append("今天是 ")
                    append(java.time.LocalDate.now())
                    append("。以下是刚检索到的资料，按 [编号] 排列。\n\n")
                    append("回答要求：\n1. 只依据这些资料回答，没有的信息不要编造。\n")
                    append("2. 引用处用 [编号] 标注。\n3. 资料冲突时指出冲突。\n\n")
                    append("=== 检索资料 ===\n")
                    append(outcome.first)
                }
                emit(ChatStreamEvent.Citations(outcome.second))
            }
        }

        val prompt = if (continueFrom != null) {
            "请直接接着你上一条回答继续输出剩余内容。不要重复已经说过的部分，不要加任何开场白。"
        } else {
            text
        }

        val assistant = withContext(Dispatchers.IO) {
            store.appendMessage(
                conversationId = conversationId,
                role = "assistant",
                blocks = emptyList(),
                status = "streaming",
            )
        }

        emit(ChatStreamEvent.Accepted(assistant.id))

        val blocks = mutableListOf<BlockDto>()
        var textBuffer = StringBuilder()
        var reasoningBuffer = StringBuilder()
        var failed = false

        // 边转发边累积：流结束后要一次性写回本地
        chatApi.stream(
            provider = provider,
            baseURL = baseUrl,
            apiKey = apiKey,
            model = model,
            history = history,
            prompt = prompt,
            thinking = thinking,
            effort = effort,
            files = files,
            system = system,
        ).collect { event ->
            when (event) {
                is ChatStreamEvent.TextDelta -> textBuffer.append(event.text)
                is ChatStreamEvent.ReasoningDelta -> reasoningBuffer.append(event.text)
                is ChatStreamEvent.Failed -> failed = true
                else -> Unit
            }
            emit(event)
        }

        // 流结束后把累积的正文/思考写回本地
        withContext(Dispatchers.IO) {
            if (reasoningBuffer.isNotEmpty()) {
                blocks.add(BlockDto(type = "reasoning", text = reasoningBuffer.toString()))
            }
            if (textBuffer.isNotEmpty()) {
                blocks.add(
                    BlockDto(
                        type = "text",
                        text = textBuffer.toString(),
                        citations = preCitations.takeIf { it.isNotEmpty() },
                    ),
                )
            }
            store.updateMessage(
                id = assistant.id,
                blocks = blocks,
                status = if (failed) "failed" else "completed",
            )
        }
    }
}
