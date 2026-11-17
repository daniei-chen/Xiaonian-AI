package art.xu.prototype.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import art.xu.prototype.ui.theme.LocalDiColors

@Composable
fun LibraryScreen(state: DiState, onBack: () -> Unit, onContinue: (String) -> Unit) {
    val colors = LocalDiColors.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(63.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = colors.ink) }
            Text("灵感夹", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 21.dp)) {
            Spacer(Modifier.height(16.dp))
            Text("聊到的，\n不必只聊一次。", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            Text("把喜欢的回答收进来，以后从这里接着聊。", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
            Spacer(Modifier.height(27.dp))
            if (state.savedQuotes.isEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.soft).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.BookmarkBorder, null, tint = colors.accent, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("还没有收藏的灵感", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(7.dp))
                    Text("在回答下方点书签，就能留在这里。", style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
                }
            }
            state.savedQuotes.forEachIndexed { index, quote ->
                Surface(Modifier.fillMaxWidth().padding(bottom = 14.dp), shape = RoundedCornerShape(20.dp), color = colors.card) {
                    Column(Modifier.padding(19.dp)) {
                        Text("灵感 ${String.format("%02d", index + 1)}", color = colors.accent, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(11.dp))
                        Text(quote.text, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                        Spacer(Modifier.height(15.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.peach).quietClick { onContinue(quote.text) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("顺着这条灵感继续聊", style = MaterialTheme.typography.labelLarge, color = colors.accent, modifier = Modifier.weight(1f))
                            Text("→", color = colors.accent)
                        }
                    }
                }
            }
        }
    }
}
