package art.kaogong.xiaonian.ui.model

/** P0 占位数据；WP4 接上 Gateway 后由 ViewModel 提供 */
data class ConversationSummary(
    val id: String,
    val title: String,
)

/** 顶部分段：聊天 / 工作 */
enum class ChatMode(val label: String) {
    Chat("聊天"),
    Work("工作"),
}

val sampleConversations = listOf(
    ConversationSummary("1", "调研手机助理 Agent 的实现路径"),
    ConversationSummary("2", "把月度工资数据做成柱状图"),
    ConversationSummary("3", "整理这周的会议纪要"),
)
