package art.kaogong.xiaonian.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.theme.LocalAccent
import art.kaogong.xiaonian.ui.theme.LocalPalette
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 输入条 + 右侧主按钮。
 *
 * 两个语音入口语义不同，不要合并：
 * - 框内麦克风 = **语音输入**（ASR 转文字填进输入框，不自动发送）
 * - 框外紫色圆钮 = **语音对话模式**（进全屏双向语音页）
 * 有文字时紫色圆钮变发送；生成中变停止。
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    streaming: Boolean,
    onVoiceInput: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    onAttach: () -> Unit,
    onToggleSearch: () -> Unit = {},
    searchOn: Boolean = false,
    modifier: Modifier = Modifier,
    recording: Boolean = false,
    transcribing: Boolean = false,
    attachments: List<PendingAttachment> = emptyList(),
    uploading: Boolean = false,
    onRemoveAttachment: (String) -> Unit = {},
) {
    val hasText = value.isNotBlank()
    /** 只有附件也能发，所以发送态不能只看文字 */
    val canSend = hasText || attachments.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty() || uploading) {
            AttachmentChips(
                attachments = attachments,
                uploading = uploading,
                onRemove = onRemoveAttachment,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(LocalPalette.current.canvas)
                .border(1.dp, LocalPalette.current.divider, RoundedCornerShape(20.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ComposerIcon(Icons.Outlined.Add, "添加附件", onAttach)

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalPalette.current.ink),
                cursorBrush = SolidColor(LocalAccent.current.accent),
                maxLines = 5,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = when {
                                    recording -> "正在录音，再点一下结束"
                                    transcribing -> "识别中…"
                                    else -> "询问 小念"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (recording) Color(0xFFE11D48) else LocalPalette.current.inkTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )

            // 联网搜索：截图里是输入条内的蓝色圆圈图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = if (searchOn) "关闭联网搜索" else "联网搜索",
                    tint = if (searchOn) LocalAccent.current.accent else LocalPalette.current.ink,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 录音中给一层浅红底 + 红色图标，转录中禁用点击
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (recording) Color(0xFFFFE4E6) else Color.Transparent)
                    .clickable(enabled = !transcribing, onClick = onVoiceInput),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = if (recording) "停止录音" else "语音输入",
                    tint = when {
                        recording -> Color(0xFFE11D48)
                        transcribing -> LocalPalette.current.inkTertiary
                        else -> LocalPalette.current.ink
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(LocalPalette.current.ink)
                .clickable {
                    when {
                        streaming -> onStop()
                        canSend -> onSend()
                        else -> onOpenVoiceMode()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    streaming -> Icons.Outlined.Stop
                    canSend -> Icons.Outlined.ArrowUpward
                    else -> Icons.Outlined.GraphicEq
                },
                contentDescription = when {
                    streaming -> "停止生成"
                    canSend -> "发送"
                    else -> "语音对话"
                },
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        }
    }
}

/** 待发送附件：图片/文档 chip，可逐个移除 */
@Composable
private fun AttachmentChips(
    attachments: List<PendingAttachment>,
    uploading: Boolean,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { item ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalPalette.current.surfaceMuted)
                    .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (item.isImage) Icons.Outlined.Image else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = LocalPalette.current.inkSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalPalette.current.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { onRemove(item.fileId) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "移除附件",
                        tint = LocalPalette.current.inkSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        if (uploading) {
            Text(
                text = "上传中…",
                style = MaterialTheme.typography.labelSmall,
                color = LocalPalette.current.inkTertiary,
            )
        }
    }
}

@Composable
private fun ComposerIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = LocalPalette.current.ink,
            modifier = Modifier.size(22.dp),
        )
    }
}
