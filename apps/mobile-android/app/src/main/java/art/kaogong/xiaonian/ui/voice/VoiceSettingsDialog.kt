package art.kaogong.xiaonian.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 语音对话设置。
 *
 * 两项都真实生效：语速走 MediaPlayer 的 PlaybackParams（本地调速，不依赖上游），
 * 音色走上游的 voice_id。之前这里是个空按钮，现在有真东西可设。
 */
@Composable
fun VoiceSettingsDialog(
    rate: Float,
    voice: String,
    onDismiss: () -> Unit,
    onSave: (rate: Float, voice: String) -> Unit,
) {
    var currentRate by remember { mutableStateOf(rate) }
    var currentVoice by remember { mutableStateOf(voice) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XiaonianColors.Canvas,
        title = { Text("语音", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    "朗读语速",
                    style = MaterialTheme.typography.labelMedium,
                    color = XiaonianColors.InkTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.8f to "慢", 1f to "正常", 1.25f to "快", 1.5f to "很快").forEach { (r, label) ->
                        Chip(label, kotlin.math.abs(currentRate - r) < 0.01f) {
                            currentRate = r
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "音色",
                    style = MaterialTheme.typography.labelMedium,
                    color = XiaonianColors.InkTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("stella", "alex", "anna").forEach { name ->
                        Chip(name, currentVoice == name) { currentVoice = name }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = currentVoice,
                    onValueChange = { currentVoice = it.trim() },
                    singleLine = true,
                    label = { Text("voice_id（可自定义）") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "语速在本地生效；音色要上游支持这个 voice_id，填错会退回默认音色。",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaonianColors.InkSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(currentRate, currentVoice)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) Color.White else XiaonianColors.InkSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) XiaonianColors.Accent else XiaonianColors.SurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
