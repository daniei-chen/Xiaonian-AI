package art.kaogong.xiaonian.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 服务器地址设置。
 *
 * 这是真机安装后**唯一必须配**的东西：APK 里默认写的是 10.0.2.2，
 * 那个地址只在模拟器里指向宿主机，真机上什么都不是。
 */
@Composable
fun ServerSettingsDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onTest: (String, (Boolean) -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XiaonianColors.Canvas,
        title = { Text("服务器地址", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        testResult = null
                    },
                    singleLine = true,
                    label = { Text("http://主机:端口") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "模拟器用 10.0.2.2。真机填电脑的局域网 IP，例如 " +
                        "http://192.168.1.5:8788，手机和电脑要在同一个 Wi-Fi 下。",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaonianColors.InkSecondary,
                )

                testResult?.let { (ok, message) ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ok) Color(0xFF15803D) else Color(0xFFD33A3A),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(value)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testResult = null
                        onTest(value) { ok ->
                            testing = false
                            testResult = if (ok) {
                                true to "连接成功，可以用了"
                            } else {
                                false to "连不上：检查地址、端口，以及电脑上的 gateway 是否在跑"
                            }
                        }
                    },
                ) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
