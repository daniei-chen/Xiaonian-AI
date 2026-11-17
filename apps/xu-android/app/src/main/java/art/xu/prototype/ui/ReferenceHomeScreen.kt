package art.xu.prototype.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import art.xu.prototype.R
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun ReferenceHomeScreen(state: DiState, onDrawer: () -> Unit, onNewChat: () -> Unit, onInput: (String) -> Unit, onSend: () -> Unit, onVoice: () -> Unit, onAttach: () -> Unit, onRecent: (Int) -> Unit, onWork: () -> Unit) {
    val colors = LocalDiColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ratio = maxWidth / 1200f
        val yRatio = maxHeight / 2670f
        Image(painterResource(R.drawable.chatgpt_reference), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        fun Modifier.ref(x: Int, y: Int, w: Int, h: Int) = offset((x * ratio.value).dp, (y * yRatio.value).dp).width((w * ratio.value).dp).height((h * yRatio.value).dp)
        Box(Modifier.ref(35, 160, 145, 135).quietClick(onDrawer))
        Box(Modifier.ref(355, 160, 485, 155).quietClick(onWork))
        Box(Modifier.ref(1040, 160, 130, 135).quietClick(onNewChat))
        Box(Modifier.ref(40, 1440, 1115, 130).quietClick { onRecent(1) })
        Box(Modifier.ref(40, 1580, 1115, 130).quietClick { onRecent(2) })
        Box(Modifier.ref(40, 1720, 1115, 130).quietClick { onRecent(3) })
        Box(Modifier.ref(35, 2305, 1130, 215).clip(androidx.compose.foundation.shape.RoundedCornerShape(30.dp)).background(androidx.compose.ui.graphics.Color(0xFFF4F4F5)))
        Box(Modifier.ref(45, 2350, 115, 145).quietClick(onAttach), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(Icons.Outlined.Add, "添加附件", tint = colors.ink, modifier = Modifier.width(26.dp).height(26.dp))
        }
        BasicTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.ref(170, 2395, 770, 90),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { inner ->
                Box {
                    if (state.input.isEmpty()) Text("询问 小 D", style = MaterialTheme.typography.bodyLarge, color = colors.secondary)
                    inner()
                }
            },
        )
        Box(Modifier.ref(1000, 2350, 145, 145).clip(androidx.compose.foundation.shape.CircleShape).background(colors.accent).quietClick(if (state.input.isBlank()) onVoice else onSend), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(if (state.input.isBlank()) Icons.Outlined.GraphicEq else Icons.Outlined.ArrowUpward, if (state.input.isBlank()) "语音对话" else "发送", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.width(24.dp).height(24.dp))
        }
    }
}
