package art.kaogong.xiaonian.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 输入条「＋」弹出的附件菜单（参考截图 7）。
 *
 * 拍照/照片/文件 P0 未接（灰显），联网搜索与深入思考是真的开关，
 * 选中项右侧打勾。这里刻意不把它们做成两个独立按钮——参考图里它们
 * 就在这个菜单里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    webSearch: Boolean,
    deepSearch: Boolean,
    thinking: Boolean,
    effort: String,
    onToggleWebSearch: () -> Unit,
    onToggleDeepSearch: () -> Unit,
    onToggleThinking: () -> Unit,
    onEffortChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPickOcr: () -> Unit,
    onReadLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = XiaonianColors.Canvas,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(XiaonianColors.SurfaceRaised),
                )
            }
        },
    ) {
        Column(modifier = Modifier.padding(bottom = 26.dp)) {
            SheetItem(Icons.Outlined.PhotoCamera, "拍照", enabled = false)
            SheetItem(Icons.Outlined.Image, "照片", onClick = onPickImage)
            SheetItem(Icons.Outlined.AttachFile, "文件", onClick = onPickFile)
            SheetItem(Icons.Outlined.TextSnippet, "图片转文字", onClick = onPickOcr)
            SheetItem(Icons.Outlined.Link, "读链接", onClick = onReadLink)
            SheetItem(
                icon = Icons.Outlined.Public,
                label = "联网搜索",
                selected = webSearch,
                onClick = onToggleWebSearch,
            )
            SheetItem(
                icon = Icons.Outlined.TravelExplore,
                label = "深度搜索",
                selected = deepSearch,
                onClick = onToggleDeepSearch,
            )
            SheetItem(
                icon = Icons.Outlined.Psychology,
                label = "深入思考",
                selected = thinking,
                onClick = onToggleThinking,
            )

            // 强度只有开了思考才有意义，否则整行不显示
            if (thinking) {
                EffortRow(current = effort, onChange = onEffortChange)
            }
        }
    }
}

/** 思考强度三档：高（默认，快）/ 最高（更深的推理） */
@Composable
private fun EffortRow(current: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 79.dp, end = 22.dp, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("high" to "高", "max" to "最高").forEach { (value, label) ->
            val active = current == value
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color.White else XiaonianColors.InkSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) XiaonianColors.Accent else XiaonianColors.SurfaceMuted)
                    .clickable { onChange(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Text(
            text = "思考强度",
            style = MaterialTheme.typography.labelSmall,
            color = XiaonianColors.InkTertiary,
        )
    }
}

@Composable
private fun SheetItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(XiaonianColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) XiaonianColors.Ink else XiaonianColors.InkTertiary,
                modifier = Modifier.size(21.dp),
            )
        }

        Spacer(Modifier.width(13.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) XiaonianColors.Ink else XiaonianColors.InkTertiary,
            modifier = Modifier.weight(1f),
        )

        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已开启",
                tint = XiaonianColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
