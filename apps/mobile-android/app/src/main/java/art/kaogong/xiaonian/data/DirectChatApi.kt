package art.kaogong.xiaonian.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 直连上游的聊天客户端。
 *
 * 两种协议都要支持，因为它们的用处不同：
 *  - zhihui（OpenAI 兼容）：便宜、快，日常问答
 *  - deepseek（Anthropic 兼容）：文件引用、思考签名、引用编号
 *
 * 所有密钥都在本机，请求不经任何中间服务。
 */
class DirectChatApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // 流式响应不能设读超时，否则长回答会被掐断
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------- 校验

    /** 探一下密钥能不能用：拉一次模型列表，200 就算通 */
    suspend fun verify(provider: String, baseURL: String, apiKey: String): Pair<Boolean, String> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext false to "先填 API Key"
            val path = if (provider == Settings.PROVIDER_DEEPSEEK) "/anthropic/v1/models" else "/models"
            val request = Request.Builder()
                .url("${baseURL.trimEnd('/')}$path")
                .apply {
                    if (provider == Settings.PROVIDER_DEEPSEEK) {
                        header("x-api-key", apiKey)
                        header("anthropic-version", "2023-06-01")
                    } else {
                        header("Authorization", "Bearer $apiKey")
                    }
                }
                .get()
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        true to "连接成功，可以用了"
                    } else {
                        false to "被拒绝（HTTP ${response.code}）：密钥可能无效或过期"
                    }
                }
            }.getOrElse { false to "连不上：${it.message ?: "网络错误"}" }
        }

    // ---------------------------------------------------------------- 对外

    fun stream(
        provider: String,
        baseURL: String,
        apiKey: String,
        model: String,
        history: List<MessageDto>,
        prompt: String,
        thinking: Boolean,
        effort: String,
        files: List<FileRef>,
        system: String? = null,
    ): Flow<ChatStreamEvent> = callbackFlow {
        val request = if (provider == Settings.PROVIDER_DEEPSEEK) {
            anthropicRequest(baseURL, apiKey, model, history, prompt, thinking, effort, files, system)
        } else {
            openAiRequest(baseURL, apiKey, model, history, prompt, thinking, system)
        }

        val call = client.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty().take(300)
                    trySend(ChatStreamEvent.Failed("upstream_${response.code}", body.ifBlank { "请求失败" }))
                    close()
                    return@use
                }

                val source = response.body?.source()
                if (source == null) {
                    trySend(ChatStreamEvent.Failed("protocol_violation", "上游没有返回内容"))
                    close()
                    return@use
                }

                if (provider == Settings.PROVIDER_DEEPSEEK) {
                    readAnthropic(source, this)
                } else {
                    readOpenAi(source, this)
                }
            }
        } catch (e: IOException) {
            trySend(ChatStreamEvent.Failed("network", e.message ?: "网络错误"))
        } finally {
            trySend(ChatStreamEvent.Completed("", canContinue = true))
            close()
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- OpenAI 协议

    private fun openAiRequest(
        baseURL: String,
        apiKey: String,
        model: String,
        history: List<MessageDto>,
        prompt: String,
        thinking: Boolean,
        system: String?,
    ): Request {
        val messages = buildJsonArray {
            if (!system.isNullOrBlank()) {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", system)
                })
            }
            for (m in history) {
                val text = m.blocks.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("")
                if (text.isBlank()) continue
                add(buildJsonObject {
                    put("role", if (m.role == "user") "user" else "assistant")
                    put("content", text)
                })
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("stream", true)
            put("max_tokens", 4096)
            if (thinking) put("thinking", buildJsonObject { put("type", "enabled") })
        }

        return Request.Builder()
            .url("${baseURL.trimEnd('/')}/chat/completions")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .build()
    }

    private fun readOpenAi(source: okio.BufferedSource, emitter: kotlinx.coroutines.channels.SendChannel<ChatStreamEvent>) {
        var blockIndex = 0
        var blockStarted = false

        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank() || line.startsWith(":")) continue
            if (!line.startsWith("data:")) continue

            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break

            val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
            val choice = obj["choices"]?.let { it as? JsonArray }?.firstOrNull()?.jsonObject ?: continue
            val delta = choice["delta"]?.jsonObject ?: continue

            val reasoning = delta["reasoning_content"]?.jsonPrimitive?.contentOrNull
            if (!reasoning.isNullOrEmpty()) {
                emitter.trySend(ChatStreamEvent.ReasoningDelta(0, reasoning))
            }

            val content = delta["content"]?.jsonPrimitive?.contentOrNull
            if (!content.isNullOrEmpty()) {
                if (!blockStarted) {
                    blockStarted = true
                    emitter.trySend(ChatStreamEvent.Started("", ""))
                    blockIndex = 1
                }
                emitter.trySend(ChatStreamEvent.TextDelta(blockIndex, content))
            }
        }
    }

    // ---------------------------------------------------------------- Anthropic 协议

    private fun anthropicRequest(
        baseURL: String,
        apiKey: String,
        model: String,
        history: List<MessageDto>,
        prompt: String,
        thinking: Boolean,
        effort: String,
        files: List<FileRef>,
        system: String?,
    ): Request {
        val messages = buildJsonArray {
            for (m in history) {
                val parts = buildJsonArray {
                    for (b in m.blocks) {
                        // 历史只回传正文：附件的 file_id 只在当轮有效，且 BlockDto 不存 fileId
                        val body = b.text
                        if (b.type == "text" && !body.isNullOrBlank()) {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", body)
                            })
                        }
                    }
                }
                if (parts.isEmpty()) continue
                add(buildJsonObject {
                    put("role", if (m.role == "user") "user" else "assistant")
                    put("content", parts)
                })
            }

            val current = buildJsonArray {
                for (f in files) {
                    add(buildJsonObject {
                        put("type", if (f.kind == "image") "image" else "document")
                        putJsonObject("source") {
                            put("type", "file")
                            put("file_id", f.fileId)
                        }
                    })
                }
                if (prompt.isNotBlank()) {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", prompt)
                    })
                }
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", current)
            })
        }

        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 4096)
            put("stream", true)
            put("thinking", buildJsonObject {
                put("type", if (thinking) "enabled" else "disabled")
            })
            if (thinking) {
                put("reasoning_effort", if (effort == "max") "max" else "high")
                // 用 beta 打开思考签名（回传多轮时需要）
                put("safety_check", buildJsonObject { put("level", "high") })
            }
            if (!system.isNullOrBlank()) put("system", system)
        }

        return Request.Builder()
            .url("${baseURL.trimEnd('/')}/anthropic/v1/messages")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .header("anthropic-beta", "files-api-2025-04-14")
            .build()
    }

    private fun readAnthropic(
        source: okio.BufferedSource,
        emitter: kotlinx.coroutines.channels.SendChannel<ChatStreamEvent>,
    ) {
        var anthropicIndexToBlock = mutableMapOf<Int, Int>()
        var nextBlock = 0
        val citations = mutableListOf<CitationDto>()

        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue

            val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue

            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "message_start" -> {
                    val usage = obj["message"]?.jsonObject?.get("usage")
                    if (usage != null) {
                        emitter.trySend(ChatStreamEvent.Usage(parseUsage(usage.jsonObject)))
                    }
                }

                "content_block_start" -> {
                    val index = obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val kind = obj["content_block"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                    if (kind == "thinking") {
                        val mapped = nextBlock++
                        anthropicIndexToBlock[index] = mapped
                        emitter.trySend(
                            ChatStreamEvent.BlockStart(mapped, BlockDto(type = "reasoning", text = "")),
                        )
                    } else if (kind == "text") {
                        val mapped = nextBlock++
                        anthropicIndexToBlock[index] = mapped
                        emitter.trySend(
                            ChatStreamEvent.BlockStart(mapped, BlockDto(type = "text", text = "")),
                        )
                    } else {
                        // server_tool_use / web_search_tool_result 之类：跳过，不进正文
                        anthropicIndexToBlock[index] = -1
                    }
                }

                "content_block_delta" -> {
                    val index = obj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    val mapped = anthropicIndexToBlock[index] ?: -1
                    if (mapped < 0) continue

                    val delta = obj["delta"]?.jsonObject ?: continue
                    when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                        "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.let {
                            emitter.trySend(ChatStreamEvent.TextDelta(mapped, it))
                        }
                        "thinking_delta" -> delta["thinking"]?.jsonPrimitive?.contentOrNull?.let {
                            emitter.trySend(ChatStreamEvent.ReasoningDelta(mapped, it))
                        }
                        "citations_delta" -> {
                            val c = delta["citation"]?.jsonObject ?: continue
                            val url = c["url"]?.jsonPrimitive?.contentOrNull ?: continue
                            if (citations.none { it.url == url }) {
                                citations.add(
                                    CitationDto(
                                        index = citations.size + 1,
                                        url = url,
                                        title = c["title"]?.jsonPrimitive?.contentOrNull,
                                    ),
                                )
                                emitter.trySend(ChatStreamEvent.Citations(citations.toList()))
                            }
                        }
                    }
                }

                "message_delta" -> {
                    obj["usage"]?.jsonObject?.let {
                        emitter.trySend(ChatStreamEvent.Usage(parseUsage(it)))
                    }
                }

                "message_stop" -> return
            }
        }
    }

    private fun parseUsage(usage: JsonObject): UsageDto {
        fun int(key: String) = usage[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        return UsageDto(
            inputTokens = int("input_tokens"),
            outputTokens = int("output_tokens"),
            thinkingTokens = int("completion_thinking_tokens"),
        )
    }
}
