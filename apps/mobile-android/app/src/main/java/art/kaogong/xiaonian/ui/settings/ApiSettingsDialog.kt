package art.kaogong.xiaonian.ui.settings

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.data.Settings
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/**
 * 上游配置。
 *
 * 全本地模式下 app 直连上游，所以这里要的就是 provider + 密钥。
 * 密钥存本机 SharedPreferences，不上传任何地方。
 */
@Composable
fun ApiSettingsDialog(
    provider: String,
    apiKey: String,
    model: String,
    onDismiss: () -> Unit,
    onSave: (provider: String, apiKey: String, model: String) -> Unit,
    onTest: (provider: String, apiKey: String, model: String, (Boolean, String) -> Unit) -> Unit,
) {
    var currentProvider by remember { mutableStateOf(provider) }
    var key by remember { mutableStateOf(apiKey) }
    var modelName by remember { mutableStateOf(model) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = XiaonianColors.Canvas,
        title = { Text("模型与密钥", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderChip(
                        label = "智汇（快）",
                        active = currentProvider == Settings.PROVIDER_ZHIHUI,
                        onClick = {
                            currentProvider = Settings.PROVIDER_ZHIHUI
                            result = null
                        },
                    )
                    ProviderChip(
                        label = "DeepSeek 官方",
                        active = currentProvider == Settings.PROVIDER_DEEPSEEK,
                        onClick = {
                            currentProvider = Settings.PROVIDER_DEEPSEEK
                            result = null
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it
                        result = null
                    },
                    singleLine = true,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    singleLine = true,
                    label = { Text("模型名") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (currentProvider == Settings.PROVIDER_DEEPSEEK) {
                        "官方接口：支持文件引用与引用编号。密钥在 platform.deepseek.com 申请。"
                    } else {
                        "智汇接口：便宜、快，日常问答用这个就够。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaonianColors.InkSecondary,
                )

                result?.let { (ok, message) ->
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
                onSave(currentProvider, key, modelName)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !testing && key.isNotBlank(),
                    onClick = {
                        testing = true
                        result = null
                        onTest(currentProvider, key, modelName) { ok, message ->
                            testing = false
                            result = ok to message
                        }
                    },
                ) {
                    Text(if (testing) "测试中…" else "测试")
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun ProviderChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) Color.White else XiaonianColors.InkSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) XiaonianColors.Accent else XiaonianColors.SurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
