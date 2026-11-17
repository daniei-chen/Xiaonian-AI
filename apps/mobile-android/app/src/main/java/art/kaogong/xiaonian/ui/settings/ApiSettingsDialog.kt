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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.data.Settings
import art.kaogong.xiaonian.ui.theme.AccentTheme
import art.kaogong.xiaonian.ui.theme.LocalAccent
import art.kaogong.xiaonian.ui.theme.LocalPalette
import art.kaogong.xiaonian.ui.theme.XiaonianColors

/** 设置页需要读写的全部状态 */
data class SettingsValues(
    val provider: String,
    val apiKey: String,
    val model: String,
    val defaultThinking: Boolean,
    val defaultEffort: String,
    val fontScale: Float,
)

/**
 * 设置。
 *
 * 每一项都要真的影响行为，否则不该出现在这里：
 *  - provider / key / model → 下一次请求就用
 *  - 默认思考 + 强度 → 新对话的初始状态
 *  - 字号 → 回答正文
 *  - 清除对话 → 真的删库
 */
@Composable
fun SettingsDialog(
    initial: SettingsValues,
    versionName: String,
    onDismiss: () -> Unit,
    onSave: (SettingsValues) -> Unit,
    onTest: (provider: String, apiKey: String, (Boolean, String) -> Unit) -> Unit,
    onClearAll: () -> Unit,
    onAccentChange: (AccentTheme) -> Unit,
    darkMode: String = "system",
    onDarkModeChange: (String) -> Unit = {},
) {
    var values by remember { mutableStateOf(initial) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.canvas,
        title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel("外观")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统").forEach { (id, label) ->
                        Chip(label, darkMode == id) { onDarkModeChange(id) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionLabel("主题色")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentTheme.entries.forEach { theme ->
                        val active = theme == LocalAccent.current
                        Text(
                            text = theme.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) Color.White else LocalPalette.current.inkSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) theme.accent else LocalPalette.current.surfaceMuted)
                                .clickable { onAccentChange(theme) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LocalPalette.current.divider)
                Spacer(Modifier.height(12.dp))

                SectionLabel("模型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        "智汇（快）",
                        values.provider == Settings.PROVIDER_ZHIHUI,
                    ) {
                        values = values.copy(provider = Settings.PROVIDER_ZHIHUI)
                        result = null
                    }
                    Chip(
                        "DeepSeek 官方",
                        values.provider == Settings.PROVIDER_DEEPSEEK,
                    ) {
                        values = values.copy(provider = Settings.PROVIDER_DEEPSEEK)
                        result = null
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = values.apiKey,
                    onValueChange = {
                        values = values.copy(apiKey = it)
                        result = null
                    },
                    singleLine = true,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = values.model,
                    onValueChange = { values = values.copy(model = it) },
                    singleLine = true,
                    label = { Text("模型名") },
                    modifier = Modifier.fillMaxWidth(),
                )

                result?.let { (ok, message) ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ok) Color(0xFF15803D) else Color(0xFFD33A3A),
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LocalPalette.current.divider)
                Spacer(Modifier.height(12.dp))

                SectionLabel("默认行为")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("新对话默认深入思考", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后每次新对话直接带思考，不用再手动点",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalPalette.current.inkSecondary,
                        )
                    }
                    Switch(
                        checked = values.defaultThinking,
                        onCheckedChange = { values = values.copy(defaultThinking = it) },
                    )
                }

                if (values.defaultThinking) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("高（快）", values.defaultEffort == "high") {
                            values = values.copy(defaultEffort = "high")
                        }
                        Chip("最高（深）", values.defaultEffort == "max") {
                            values = values.copy(defaultEffort = "max")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("回答字号", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.9f to "小", 1f to "标准", 1.15f to "大", 1.3f to "特大").forEach { (scale, label) ->
                        Chip(label, kotlin.math.abs(values.fontScale - scale) < 0.01f) {
                            values = values.copy(fontScale = scale)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LocalPalette.current.divider)
                Spacer(Modifier.height(12.dp))

                SectionLabel("数据")
                Text(
                    text = "会话只存在这台手机上（SQLite），没有云端副本。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalPalette.current.inkSecondary,
                )
                Spacer(Modifier.height(8.dp))
                if (!confirmingClear) {
                    TextButton(onClick = { confirmingClear = true }) {
                        Text("清除所有对话", color = Color(0xFFD33A3A))
                    }
                } else {
                    Text(
                        "确定删除全部会话？删了就没了，建议先导出。",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD33A3A),
                    )
                    Row {
                        TextButton(onClick = {
                            onClearAll()
                            confirmingClear = false
                        }) { Text("确定删除", color = Color(0xFFD33A3A)) }
                        TextButton(onClick = { confirmingClear = false }) { Text("取消") }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = LocalPalette.current.divider)
                Spacer(Modifier.height(12.dp))

                SectionLabel("关于")
                Text(
                    text = "小念 $versionName · 全本地运行\n" +
                        "聊天走 ${if (values.provider == Settings.PROVIDER_DEEPSEEK) "api.deepseek.com" else "zhihui.kaogong.art"}\n" +
                        "搜索 / OCR / 语音固定走 DeepSeek 官方接口",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalPalette.current.inkSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(values)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !testing && values.apiKey.isNotBlank(),
                    onClick = {
                        testing = true
                        result = null
                        onTest(values.provider, values.apiKey) { ok, message ->
                            testing = false
                            result = ok to message
                        }
                    },
                ) { Text(if (testing) "测试中…" else "测试") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalPalette.current.inkTertiary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) Color.White else LocalPalette.current.inkSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) LocalAccent.current.accent else LocalPalette.current.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
