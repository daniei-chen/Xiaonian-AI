package art.kaogong.xiaonian.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Mic
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
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 输入条：一个大圆角灰底容器 + 容器外右侧的紫色圆形主按钮。
 * 有文字时紫色按钮变发送，无文字时是语音 —— 与截图里的默认态一致。
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasText = value.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(XiaonianColors.SurfaceMuted)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ComposerIcon(Icons.Outlined.Add, "添加附件", onAttach)

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 15.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = XiaonianColors.Ink),
                cursorBrush = SolidColor(XiaonianColors.Accent),
                maxLines = 5,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "询问 小念",
                                style = MaterialTheme.typography.bodyLarge,
                                color = XiaonianColors.InkTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )

            ComposerIcon(Icons.Outlined.Mic, "语音输入", onVoice)
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(XiaonianColors.Accent)
                .clickable { if (hasText) onSend() else onVoice() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (hasText) Icons.Outlined.ArrowUpward else Icons.Outlined.Mic,
                contentDescription = if (hasText) "发送" else "语音",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
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
            tint = XiaonianColors.Ink,
            modifier = Modifier.size(22.dp),
        )
    }
}
