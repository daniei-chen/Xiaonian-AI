package art.kaogong.xiaonian.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.components.CircleIconButton
import art.kaogong.xiaonian.ui.model.ChatMode
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 顶栏：左右各一个圆形按钮，中间胶囊分段控件。
 * 分段控件必须真正居中（截图如此），所以用 Box 叠加而不是 Row 分配权重。
 */
@Composable
fun ChatTopBar(
    mode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        ModeSwitch(
            mode = mode,
            onModeChange = onModeChange,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.Outlined.Menu,
                contentDescription = "打开菜单",
                onClick = onOpenDrawer,
            )
            CircleIconButton(
                icon = Icons.Outlined.Edit,
                contentDescription = "新对话",
                onClick = onNewChat,
            )
        }
    }
}

@Composable
private fun ModeSwitch(
    mode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(XiaonianColors.SurfaceRaised)
            .padding(3.dp),
    ) {
        ChatMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(Color.White) else Modifier)
                    .clickable { onModeChange(item) }
                    .padding(horizontal = 26.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) XiaonianColors.Ink else XiaonianColors.InkSecondary,
                )
            }
        }
    }
}
