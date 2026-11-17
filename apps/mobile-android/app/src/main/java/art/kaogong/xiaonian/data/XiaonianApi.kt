package art.kaogong.xiaonian.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

// ---------------------------------------------------------------- DTO

@Serializable
data class ConversationDto(
    val id: String,
    val title: String? = null,
    val lastMessageAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class CitationDto(
    val index: Int = 0,
    val url: String,
    val title: String? = null,
)

@Serializable
data class BlockDto(
    val type: String,
    val text: String? = null,
    val citations: List<CitationDto>? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    val role: String,
    val blocks: List<BlockDto> = emptyList(),
    val status: String = "completed",
    val seq: Long = 0,
)

@Serializable
data class ConversationDetailDto(
    val conversation: ConversationDto,
    val messages: List<MessageDto> = emptyList(),
)

@Serializable
data class UploadedFileDto(
    val fileId: String,
    val name: String,
    val mime: String,
    val size: Long = 0,
    val purpose: String? = null,
)

@Serializable
data class FileRef(
    val fileId: String,
    val kind: String,
    val name: String,
    val mime: String,
    val size: Long = 0,
)

@Serializable
data class ImportResultDto(
    val conversations: Int = 0,
    val messages: Int = 0,
)

@Serializable
private data class ConversationListDto(val items: List<ConversationDto> = emptyList())

// ---------------------------------------------------------------- 流式事件

sealed interface ChatStreamEvent {
    data class Accepted(val assistantMessageId: String) : ChatStreamEvent
    data class Started(val provider: String, val model: String) : ChatStreamEvent
    data class TextDelta(val index: Int, val text: String) : ChatStreamEvent
    data class ReasoningDelta(val index: Int, val text: String) : ChatStreamEvent
    data class Citations(val items: List<CitationDto>) : ChatStreamEvent
    data class Completed(val messageId: String, val canContinue: Boolean) : ChatStreamEvent
    data class Failed(val code: String, val message: String) : ChatStreamEvent
}

// ---------------------------------------------------------------- API

class XiaonianApi(private val baseUrl: String = ApiConfig.BASE_URL) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE 不能有读超时
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String) = baseUrl.trimEnd('/') + path

    suspend fun listConversations(): List<ConversationDto> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/api/v1/conversations")).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("list failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            json.decodeFromString(ConversationListDto.serializer(), body).items
        }
    }

    suspend fun createConversation(): ConversationDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/v1/conversations"))
            .post("{}".toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("create failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            json.decodeFromString(ConversationDto.serializer(), body)
        }
    }

    suspend fun loadConversation(id: String): ConversationDetailDto = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/api/v1/conversations/$id")).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("load failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            json.decodeFromString(ConversationDetailDto.serializer(), body)
        }
    }

    suspend fun stop(messageId: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/v1/messages/$messageId/stop"))
            .post("{}".toRequestBody(jsonMedia))
            .build()
        runCatching { client.newCall(request).execute().close() }
    }

    /** 语音输入：整段 WAV 上传，返回转录文本 */
    suspend fun transcribe(wav: ByteArray): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/v1/asr"))
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("asr failed: ${response.code}")
            json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    /** 上传附件：原始字节 + X-Mime / X-Filename，返回服务端 file_id */
    suspend fun uploadFile(bytes: ByteArray, filename: String, mime: String): UploadedFileDto =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url("/api/v1/files"))
                .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
                .header("X-Mime", mime)
                .header("X-Filename", java.net.URLEncoder.encode(filename, "UTF-8"))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("upload failed: ${response.code}")
                json.decodeFromString(UploadedFileDto.serializer(), body)
            }
        }

    /** 语音合成：返回音频字节（wav），直接交给播放器 */
    suspend fun synthesize(text: String, voiceId: String = "stella"): ByteArray =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("text", text)
                put("voiceId", voiceId)
                put("format", "wav")
            }.toString()

            val request = Request.Builder()
                .url(url("/api/v1/tts"))
                .post(payload.toRequestBody(jsonMedia))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("tts failed: ${response.code}")
                response.body?.bytes() ?: ByteArray(0)
            }
        }

    /** 导出全部历史为 ZIP 字节 */
    suspend fun exportHistory(): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url("/api/v1/export")).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("export failed: ${response.code}")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    /** 导入 ZIP 历史，返回新建的会话与消息数 */
    suspend fun importHistory(bytes: ByteArray): ImportResultDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url("/api/v1/import"))
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("import failed: ${response.code}")
            json.decodeFromString(ImportResultDto.serializer(), body)
        }
    }

    fun streamMessage(
        conversationId: String,
        text: String,
        thinking: Boolean,
        webSearch: Boolean = false,
        files: List<FileRef> = emptyList(),
        replayFromMessageId: String? = null,
        continueGeneration: Boolean = false,
    ): Flow<ChatStreamEvent> = callbackFlow {
        val payload = buildJsonObject {
            put("text", text)
            put("thinking", thinking)
            put("webSearch", webSearch)
            put("maxTokens", 2048)
            if (replayFromMessageId != null) put("replayFromMessageId", replayFromMessageId)
            if (continueGeneration) put("continueGeneration", true)
            if (files.isNotEmpty()) {
                put("files", buildJsonArray {
                    files.forEach { f ->
                        add(buildJsonObject {
                            put("fileId", f.fileId)
                            put("kind", f.kind)
                            put("name", f.name)
                            put("mime", f.mime)
                            put("size", f.size)
                        })
                    }
                })
            }
        }.toString()

        val request = Request.Builder()
            .url(url("/api/v1/conversations/$conversationId/messages"))
            .post(payload.toRequestBody(jsonMedia))
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (type == null) return
                val parsed = parseEvent(type, data) ?: return
                trySend(parsed)
                if (parsed is ChatStreamEvent.Completed || parsed is ChatStreamEvent.Failed) {
                    close()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("stream failed: ${response?.code}"))
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private fun parseEvent(type: String, data: String): ChatStreamEvent? {
        val obj: JsonObject = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        return when (type) {
            "message.accepted" -> ChatStreamEvent.Accepted(
                obj["assistantMessageId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            "message.started" -> ChatStreamEvent.Started(
                obj["provider"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                obj["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            "text.delta" -> ChatStreamEvent.TextDelta(
                obj["index"]?.jsonPrimitive?.intOrNull ?: 0,
                obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            "reasoning.delta" -> ChatStreamEvent.ReasoningDelta(
                obj["index"]?.jsonPrimitive?.intOrNull ?: 0,
                obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            "citations" -> {
                val arr = obj["items"]?.jsonArray ?: return null
                ChatStreamEvent.Citations(
                    arr.mapNotNull { item ->
                        val o = item.jsonObject
                        val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        CitationDto(
                            index = o["index"]?.jsonPrimitive?.intOrNull ?: 0,
                            url = url,
                            title = o["title"]?.jsonPrimitive?.contentOrNull,
                        )
                    },
                )
            }
            "message.completed" -> ChatStreamEvent.Completed(
                obj["messageId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                obj["canContinue"]?.jsonPrimitive?.contentOrNull == "true",
            )
            "message.failed" -> ChatStreamEvent.Failed(
                obj["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            else -> null
        }
    }
}
