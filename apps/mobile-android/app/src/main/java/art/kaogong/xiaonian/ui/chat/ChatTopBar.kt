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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.components.CircleIconButton
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 顶栏：左右各一个圆形按钮，中间胶囊是**上游切换**。
 * 分段控件必须真正居中（截图如此），所以用 Box 叠加而不是 Row 分配权重。
 */
@Composable
fun ChatTopBar(
    providers: List<Pair<String, String>>,
    activeProvider: String,
    onSelectProvider: (String) -> Unit,
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
        ProviderSwitch(
            providers = providers,
            activeProvider = activeProvider,
            onSelect = onSelectProvider,
            onLongPress = onOpenSettings,
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

/**
 * 顶部模型切换。
 *
 * 这里原本是「聊天 / 工作」两段，切了之后什么都不变——纯装饰。
 * 改成切换上游：智汇（便宜快）↔ DeepSeek 官方（支持文件和引用编号）。
 * 长按进设置，方便改密钥。
 */
@Composable
private fun ProviderSwitch(
    providers: List<Pair<String, String>>,
    activeProvider: String,
    onSelect: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(XiaonianColors.SurfaceRaised)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(3.dp),
    ) {
        providers.forEach { (id, label) ->
            val selected = id == activeProvider
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(if (selected) Modifier.background(Color.White) else Modifier)
                    .clickable { onSelect(id) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) XiaonianColors.Ink else XiaonianColors.InkSecondary,
                )
            }
        }
    }
}
