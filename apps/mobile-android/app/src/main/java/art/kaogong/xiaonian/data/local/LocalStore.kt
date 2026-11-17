package art.kaogong.xiaonian.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import art.kaogong.xiaonian.data.BlockDto
import art.kaogong.xiaonian.data.ConversationDto
import art.kaogong.xiaonian.data.MessageDto
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 本地会话存储。
 *
 * 手写 SQLiteOpenHelper 而不是 Room：这里只有两张表、几个查询，
 * 上 Room 还得加 KSP，构建复杂度和收益不匹配。
 *
 * 所有方法都是同步的，调用方负责放到 IO 线程。
 */
class LocalStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "xiaonian.db"
        private const val DB_VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private const val SQL_CONVERSATIONS = """
            CREATE TABLE conversations (
                id TEXT PRIMARY KEY,
                title TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """

        private const val SQL_MESSAGES = """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                role TEXT NOT NULL,
                blocks TEXT NOT NULL,
                status TEXT NOT NULL,
                seq INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
        """

        private const val SQL_INDEX = """
            CREATE INDEX idx_messages_conversation ON messages(conversation_id, seq)
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CONVERSATIONS)
        db.execSQL(SQL_MESSAGES)
        db.execSQL(SQL_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 增量迁移，**绝不删表**：用户的会话历史不该因为一次升级就没了。
        //
        // 好消息是 messages 的内容以 JSON 存在 blocks 列里，加字段（signature、
        // fileId 之类）不需要动表结构；只有加列/加索引/改语义才需要写在这里。
        var version = oldVersion

        // 示例（当前还没有 v2，留着说明写法）：
        // if (version < 2) {
        //     db.execSQL("ALTER TABLE messages ADD COLUMN tokens INTEGER NOT NULL DEFAULT 0")
        //     version = 2
        // }

        if (version != newVersion) {
            // 没有任何迁移步骤却要升级版本号：说明有人只改了 DB_VERSION。
            // 与其静默留下结构不一致的库，不如明确失败。
            error("no migration from v$oldVersion to v$newVersion; add one in onUpgrade")
        }
    }

    // ---------------------------------------------------------------- 会话

    fun listConversations(): List<ConversationDto> {
        val sql = "SELECT id, title, created_at, updated_at FROM conversations ORDER BY updated_at DESC"
        return readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ConversationDto(
                            id = cursor.getString(0),
                            title = cursor.getString(1),
                            createdAt = isoFrom(cursor.getLong(2)),
                            lastMessageAt = isoFrom(cursor.getLong(3)),
                        ),
                    )
                }
            }
        }
    }

    fun getConversation(id: String): ConversationDto? {
        val sql = "SELECT id, title, created_at, updated_at FROM conversations WHERE id = ?"
        return readableDatabase.rawQuery(sql, arrayOf(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            ConversationDto(
                id = cursor.getString(0),
                title = cursor.getString(1),
                createdAt = isoFrom(cursor.getLong(2)),
                lastMessageAt = isoFrom(cursor.getLong(3)),
            )
        }
    }

    fun createConversation(title: String? = null): ConversationDto {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        writableDatabase.insert(
            "conversations",
            null,
            ContentValues().apply {
                put("id", id)
                put("title", title)
                put("created_at", now)
                put("updated_at", now)
            },
        )
        return ConversationDto(id = id, title = title, createdAt = isoFrom(now), lastMessageAt = isoFrom(now))
    }

    fun setTitleIfEmpty(conversationId: String, title: String) {
        writableDatabase.execSQL(
            "UPDATE conversations SET title = ? WHERE id = ? AND (title IS NULL OR title = '')",
            arrayOf(title, conversationId),
        )
    }

    fun touchConversation(conversationId: String) {
        writableDatabase.execSQL(
            "UPDATE conversations SET updated_at = ? WHERE id = ?",
            arrayOf(System.currentTimeMillis(), conversationId),
        )
    }

    // ---------------------------------------------------------------- 消息

    fun listMessages(conversationId: String): List<MessageDto> {
        val sql = "SELECT id, role, blocks, status, seq FROM messages WHERE conversation_id = ? ORDER BY seq"
        return readableDatabase.rawQuery(sql, arrayOf(conversationId)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MessageDto(
                            id = cursor.getString(0),
                            role = cursor.getString(1),
                            blocks = decodeBlocks(cursor.getString(2)),
                            status = cursor.getString(3),
                            seq = cursor.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    fun appendMessage(
        conversationId: String,
        role: String,
        blocks: List<BlockDto>,
        status: String = "completed",
    ): MessageDto {
        val id = UUID.randomUUID().toString()
        val seq = nextSeq(conversationId)
        writableDatabase.insert(
            "messages",
            null,
            ContentValues().apply {
                put("id", id)
                put("conversation_id", conversationId)
                put("role", role)
                put("blocks", json.encodeToString<List<BlockDto>>(blocks))
                put("status", status)
                put("seq", seq)
                put("created_at", System.currentTimeMillis())
            },
        )
        touchConversation(conversationId)
        return MessageDto(id = id, role = role, blocks = blocks, status = status, seq = seq)
    }

    fun updateMessage(id: String, blocks: List<BlockDto>, status: String) {
        writableDatabase.execSQL(
            "UPDATE messages SET blocks = ?, status = ? WHERE id = ?",
            arrayOf(json.encodeToString<List<BlockDto>>(blocks), status, id),
        )
    }

    fun deleteMessagesAfter(conversationId: String, seq: Long) {
        writableDatabase.execSQL(
            "DELETE FROM messages WHERE conversation_id = ? AND seq >= ?",
            arrayOf(conversationId, seq),
        )
    }

    fun messageSeq(messageId: String): Long? {
        return readableDatabase.rawQuery("SELECT seq FROM messages WHERE id = ?", arrayOf(messageId))
            .use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    fun conversationIdOfMessage(messageId: String): String? {
        return readableDatabase
            .rawQuery("SELECT conversation_id FROM messages WHERE id = ?", arrayOf(messageId))
            .use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun nextSeq(conversationId: String): Long {
        return readableDatabase
            .rawQuery("SELECT COALESCE(MAX(seq), 0) + 1 FROM messages WHERE conversation_id = ?", arrayOf(conversationId))
            .use { if (it.moveToFirst()) it.getLong(0) else 1L }
    }

    /** 清除全部会话与消息（设置里的「清除所有对话」） */
    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM messages")
        writableDatabase.execSQL("DELETE FROM conversations")
    }

    private fun decodeBlocks(raw: String): List<BlockDto> =
        runCatching { json.decodeFromString<List<BlockDto>>(raw) }.getOrDefault(emptyList())

    private fun isoFrom(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).toString()
}
