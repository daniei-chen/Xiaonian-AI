package art.kaogong.xiaonian.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.markdown.MarkdownText
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 消息列表。
 * 用户消息右对齐灰气泡；助手消息无气泡铺满宽度——这是截图里 ChatGPT 的做法，
 * 长回答不会被气泡挤成窄条。
 */
@Composable
fun MessageList(
    messages: List<UiMessage>,
    onToggleReasoning: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 新消息与流式增量都要跟到底部，否则回答和引用会落在可视区外
    val last = messages.lastOrNull()
    LaunchedEffect(
        messages.size,
        last?.id,
        last?.text?.length ?: 0,
        last?.reasoning?.length ?: 0,
        last?.citations?.size ?: 0,
    ) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = 100_000)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            if (message.fromUser) {
                UserBubble(message)
            } else {
                AssistantMessage(
                    message = message,
                    onToggleReasoning = onToggleReasoning,
                    onRegenerate = onRegenerate,
                    onContinue = onContinue,
                    isLast = index == messages.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun UserBubble(message: UiMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 296.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(XiaonianColors.SurfaceMuted)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = XiaonianColors.Ink,
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    message: UiMessage,
    onToggleReasoning: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onContinue: () -> Unit,
    isLast: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        val showThinking = message.reasoning.isNotBlank() || message.streaming

        if (showThinking) {
            ThinkingHeader(message, onToggleReasoning)

            if (message.reasoningExpanded && message.reasoning.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message.reasoning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = XiaonianColors.InkSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .padding(12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        when {
            message.text.isNotBlank() -> MarkdownText(markdown = message.text)
            message.streaming -> TypingDots()
            message.failed -> Text(
                text = "生成失败，点上方按钮重试",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD33A3A),
            )
        }

        if (message.citations.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            CitationList(message.citations)
        }

        if (!message.streaming && message.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MessageAction("重新生成") { onRegenerate(message.id) }
                if (isLast) MessageAction("继续") { onContinue() }
            }
        }
    }
}

/** 助手消息下方的小操作，低饱和以免和正文抢注意力 */
@Composable
private fun MessageAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = XiaonianColors.InkSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** 引用来源：序号圆 + 标题 + 域名。正文里的 [n] 与之对应 */
@Composable
private fun CitationList(citations: List<UiCitation>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        citations.forEach { citation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF7F7F8))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(XiaonianColors.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${citation.index}",
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaonianColors.AccentDeep,
                    )
                }

                Spacer(Modifier.width(9.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = citation.title ?: citation.host,
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiaonianColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = citation.host,
                        style = MaterialTheme.typography.labelSmall,
                        color = XiaonianColors.InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingHeader(message: UiMessage, onToggleReasoning: (String) -> Unit) {
    val label = when {
        message.streaming && message.text.isBlank() -> "正在思考"
        message.reasoning.isNotBlank() -> "思考过程"
        else -> "思考中"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggleReasoning(message.id) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = XiaonianColors.InkSecondary,
        )
        Icon(
            imageVector = if (message.reasoningExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = XiaonianColors.InkSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620, delayMillis = index * 150, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(XiaonianColors.InkSecondary.copy(alpha = alpha)),
            )
        }
    }
}
