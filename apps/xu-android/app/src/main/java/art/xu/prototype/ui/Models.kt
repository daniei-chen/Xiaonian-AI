package art.xu.prototype.ui

data class Conversation(
    val id: Int,
    val title: String,
    val group: String = "今天",
    val messages: List<ChatMessage> = emptyList(),
)

data class ChatMessage(
    val id: Int,
    val fromUser: Boolean,
    val text: String,
    val sources: List<String> = emptyList(),
    val thinking: String = "",
)

data class SavedQuote(
    val text: String,
    val conversationTitle: String,
)

data class DiState(
    val conversations: List<Conversation> = listOf(
        Conversation(1, "帮我规划一个轻松的周末"),
        Conversation(2, "把这段文字改得更自然"),
        Conversation(3, "如何开始一门外语", "过去 7 天"),
        Conversation(4, "阳台植物养护清单", "过去 30 天"),
    ),
    val activeId: Int? = null,
    val nextConversationId: Int = 5,
    val nextMessageId: Int = 0,
    val input: String = "",
    val drawerOpen: Boolean = false,
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val attachmentOpen: Boolean = false,
    val voiceOpen: Boolean = false,
    val listening: Boolean = false,
    val settingsOpen: Boolean = false,
    val libraryOpen: Boolean = false,
    val personalityOpen: Boolean = false,
    val webSearch: Boolean = false,
    val deepThinking: Boolean = false,
    val attachmentName: String? = null,
    val personality: String = "温暖",
    val dark: Boolean = false,
    val reducedMotion: Boolean = false,
    val savedQuotes: List<SavedQuote> = emptyList(),
    val notice: String? = null,
    val referenceHome: Boolean = true,
) {
    val messages get() = conversations.find { it.id == activeId }?.messages.orEmpty()
    val activeTitle get() = conversations.find { it.id == activeId }?.title ?: "小 D"
}
