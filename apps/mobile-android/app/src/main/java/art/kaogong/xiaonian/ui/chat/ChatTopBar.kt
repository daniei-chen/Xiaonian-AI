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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.components.CircleIconButton
import art.kaogong.xiaonian.ui.theme.LocalPalette
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 顶栏：左右各一个圆形按钮，中间显示当前模型（只读，点了进设置）。
 *
 * 这里**不放供应商切换**：那是低频配置，属于设置页。之前的版本把
 * 「聊天/工作」装饰换成了模型切换，等于把配置项塞进高频操作区，是错的。
 */
@Composable
fun ChatTopBar(
    modelLabel: String,
    onOpenSettings: () -> Unit,
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
        Text(
            text = modelLabel,
            style = MaterialTheme.typography.labelLarge,
            color = LocalPalette.current.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 12.dp, vertical = 6.dp),
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

