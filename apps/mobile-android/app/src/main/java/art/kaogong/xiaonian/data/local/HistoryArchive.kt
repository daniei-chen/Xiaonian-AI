package art.kaogong.xiaonian.data.local

import art.kaogong.xiaonian.data.BlockDto
import art.kaogong.xiaonian.data.MessageDto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 历史导入导出（v3 结构）。
 *
 * 目录布局与 DeepSeek / chat-demo 一致：
 *   manifest.json -> chat_sessions/{id}.json -> chat_session_index.json
 * 这样导出的包两边都能读。
 */
object HistoryArchive {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class Manifest(val export_format_version: Int = 3)

    @Serializable
    private data class ArchiveFragment(val type: String, val content: String? = null)

    @Serializable
    private data class ArchiveMessage(
        val id: Int,
        val parent_id: Int? = null,
        val role: String,
        val status: String = "FINISHED",
        val thinking_enabled: Boolean = false,
        val search_enabled: Boolean = false,
        val accumulated_token_usage: Int = 0,
        val inserted_at: String,
        val fragments: List<ArchiveFragment> = emptyList(),
    )

    @Serializable
    private data class ArchiveSession(
        val id: String,
        val title: String? = null,
        val pinned: Boolean = false,
        val current_message_id: Int = 0,
        val inserted_at: String,
        val updated_at: String,
        val messages: List<ArchiveMessage> = emptyList(),
    )

    @Serializable
    private data class IndexEntry(
        val id: String,
        val title: String? = null,
        val pinned: Boolean = false,
        val message_count: Int = 0,
        val inserted_at: String,
        val updated_at: String,
    )

    /** 把本机全部会话打成 zip */
    fun export(store: LocalStore): ByteArray {
        val conversations = store.listConversations()
        val sessions = ArrayList<ArchiveSession>(conversations.size)
        val index = ArrayList<IndexEntry>(conversations.size)

        for (conversation in conversations) {
            val messages = store.listMessages(conversation.id)
            val archived = ArrayList<ArchiveMessage>(messages.size)
            var parent: Int? = null

            messages.forEachIndexed { position, message ->
                val fragments = buildList {
                    for (block in message.blocks) {
                        when (block.type) {
                            "reasoning" -> block.text?.let { add(ArchiveFragment("THINK", it)) }
                            "text" -> block.text?.let {
                                add(
                                    ArchiveFragment(
                                        if (message.role == "user") "REQUEST" else "RESPONSE",
                                        it,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (fragments.isEmpty()) return@forEachIndexed

                val id = position + 1
                archived.add(
                    ArchiveMessage(
                        id = id,
                        parent_id = parent,
                        role = if (message.role == "user") "USER" else "ASSISTANT",
                        thinking_enabled = message.blocks.any { it.type == "reasoning" },
                        inserted_at = conversation.createdAt ?: nowIso(),
                        fragments = fragments,
                    ),
                )
                parent = id
            }

            val stamp = conversation.createdAt ?: nowIso()
            sessions.add(
                ArchiveSession(
                    id = conversation.id,
                    title = conversation.title,
                    current_message_id = archived.size,
                    inserted_at = stamp,
                    updated_at = conversation.lastMessageAt ?: stamp,
                    messages = archived,
                ),
            )
            index.add(
                IndexEntry(
                    id = conversation.id,
                    title = conversation.title,
                    message_count = archived.size,
                    inserted_at = stamp,
                    updated_at = conversation.lastMessageAt ?: stamp,
                ),
            )
        }

        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            // manifest 必须是第一个条目
            put("manifest.json", json.encodeToString(Manifest()))
            for (session in sessions) {
                put("chat_sessions/${session.id}.json", json.encodeToString(session))
            }
            put("chat_session_index.json", json.encodeToString(index))
        }
        return buffer.toByteArray()
    }

    /** 读回 zip；返回 (会话数, 消息数) */
    fun import(store: LocalStore, bytes: ByteArray): Pair<Int, Int> {
        var conversations = 0
        var messages = 0

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith("chat_sessions/") && name.endsWith(".json")) {
                    val raw = zip.readBytes().toString(Charsets.UTF_8)
                    val session = runCatching {
                        json.decodeFromString<ArchiveSession>(raw)
                    }.getOrNull()

                    if (session != null) {
                        val created = store.createConversation(session.title)
                        conversations++

                        for (node in session.messages.sortedBy { it.id }) {
                            val blocks = node.fragments.mapNotNull { fragment ->
                                when (fragment.type) {
                                    "REQUEST", "RESPONSE", "TEMPLATE_RESPONSE" ->
                                        fragment.content?.let { BlockDto(type = "text", text = it) }
                                    "THINK" -> fragment.content?.let { BlockDto(type = "reasoning", text = it) }
                                    else -> null
                                }
                            }
                            if (blocks.isEmpty()) continue
                            store.appendMessage(
                                conversationId = created.id,
                                role = if (node.role == "ASSISTANT") "assistant" else "user",
                                blocks = blocks,
                            )
                            messages++
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return conversations to messages
    }

    private fun nowIso(): String = java.time.Instant.now().toString()
}
