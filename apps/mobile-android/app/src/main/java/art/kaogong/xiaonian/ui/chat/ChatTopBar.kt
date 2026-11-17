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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.model.ChatMode
import art.kaogong.xiaonian.ui.theme.LocalPalette

/**
 * 顶栏（按真机截图重构）：
 *   左「≡」圆钮 · 中「聊天 / 工作」分段 · 右声波圆钮
 *
 * 圆钮是浅灰底 + 深色图标，不是白底。
 */
@Composable
fun ChatTopBar(
    mode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        Segmented(
            mode = mode,
            onModeChange = onModeChange,
            modifier = Modifier.align(Alignment.Center),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.Outlined.Menu, "打开菜单", onOpenDrawer)
            CircleButton(Icons.Outlined.GraphicEq, "语音对话", onOpenVoiceMode)
        }
    }
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(LocalPalette.current.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = LocalPalette.current.ink,
            modifier = Modifier.height(20.dp),
        )
    }
}

/** 聊天 / 工作 两段；未选中侧是浅灰轨道 */
@Composable
private fun Segmented(
    mode: ChatMode,
    onModeChange: (ChatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(LocalPalette.current.surfaceRaised)
            .padding(3.dp),
    ) {
        ChatMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(LocalPalette.current.canvas) else Modifier)
                    .clickable { onModeChange(item) }
                    .padding(horizontal = 22.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) LocalPalette.current.ink else LocalPalette.current.inkSecondary,
                )
            }
        }
    }
}
