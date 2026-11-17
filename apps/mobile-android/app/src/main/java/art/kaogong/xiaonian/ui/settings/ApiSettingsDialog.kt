package art.kaogong.xiaonian.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import art.kaogong.xiaonian.data.Settings
import art.kaogong.xiaonian.ui.theme.AccentTheme
import art.kaogong.xiaonian.ui.theme.LocalAccent
import art.kaogong.xiaonian.ui.theme.LocalPalette

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
 * 设置页（按真机截图重构为分组卡片式）。
 *
 * 版式照截图，内容换成本地版真实拥有的东西：截图里的「账户 / 钱包 / 升级」
 * 在这里是「当前模型 / 用量 / 外观」，每一项都真的生效。
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
    conversationCount: Int = 0,
    messageCount: Int = 0,
) {
    var values by remember { mutableStateOf(initial) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    var showKeys by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalPalette.current.surfaceMuted),
    ) {
        // 顶栏：返回 + 标题 + 信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIcon(Icons.Outlined.ArrowBack, "返回", onDismiss)
            Spacer(Modifier.weight(1f))
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalPalette.current.ink,
            )
            Spacer(Modifier.weight(1f))
            CircleIcon(Icons.Outlined.Info, "关于") { showKeys = !showKeys }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ---------- 账户 ----------
            Card {
                SettingRow(
                    icon = Icons.Outlined.Person,
                    title = "模型",
                    value = values.model,
                ) { showKeys = !showKeys }
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "切换供应商",
                    value = if (values.provider == Settings.PROVIDER_DEEPSEEK) "DeepSeek 官方" else "智汇（快）",
                ) {
                    values = values.copy(
                        provider = if (values.provider == Settings.PROVIDER_DEEPSEEK) {
                            Settings.PROVIDER_ZHIHUI
                        } else {
                            Settings.PROVIDER_DEEPSEEK
                        },
                    )
                    result = null
                }
                RowDivider()
                SettingRow(
                    icon = Icons.Outlined.Security,
                    title = "密钥与安全",
                    value = if (values.apiKey.isBlank()) "未配置" else "已配置",
                ) { showKeys = !showKeys }
            }

            if (showKeys) {
                Spacer(Modifier.height(12.dp))
                Card {
                    Column(Modifier.padding(14.dp)) {
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
                            ) { Text(if (testing) "测试中…" else "测试连接") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                onSave(values)
                            }) { Text("保存") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 外观 ----------
            Card {
                SettingRow(icon = Icons.Outlined.Palette, title = "主题色") {}
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentTheme.entries.forEach { theme ->
                            val active = theme == LocalAccent.current
                            Text(
                                text = theme.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) Color.White else LocalPalette.current.inkSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) theme.accent else LocalPalette.current.surfaceRaised)
                                    .clickable { onAccentChange(theme) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                RowDivider()
                SettingRow(icon = Icons.Outlined.Tune, title = "外观") {}
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("light" to "浅色", "dark" to "深色", "system" to "跟随系统").forEach { (id, label) ->
                            val active = darkMode == id
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) Color.White else LocalPalette.current.inkSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) LocalAccent.current.accent else LocalPalette.current.surfaceRaised)
                                    .clickable { onDarkModeChange(id) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                RowDivider()
                SettingRow(icon = Icons.Outlined.TextFields, title = "回答字号") {}
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.9f to "小", 1f to "标准", 1.15f to "大", 1.3f to "特大").forEach { (scale, label) ->
                            val active = kotlin.math.abs(values.fontScale - scale) < 0.01f
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) Color.White else LocalPalette.current.inkSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) LocalAccent.current.accent else LocalPalette.current.surfaceRaised)
                                    .clickable { values = values.copy(fontScale = scale) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 行为 ----------
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = LocalPalette.current.ink,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("新对话默认深入思考", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开启后每次新对话直接带思考",
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
                    RowDivider()
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("high" to "高（快）", "max" to "最高（深）").forEach { (id, label) ->
                                val active = values.defaultEffort == id
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) Color.White else LocalPalette.current.inkSecondary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) LocalAccent.current.accent else LocalPalette.current.surfaceRaised)
                                        .clickable { values = values.copy(defaultEffort = id) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 用量与数据 ----------
            Card {
                SettingRow(
                    icon = Icons.Outlined.BarChart,
                    title = "用量",
                    value = "$conversationCount 会话 · $messageCount 条消息",
                ) {}
                RowDivider()
                if (!confirmingClear) {
                    SettingRow(
                        icon = Icons.Outlined.Security,
                        title = "清除所有对话",
                        value = "本地删除，不可恢复",
                        danger = true,
                    ) { confirmingClear = true }
                } else {
                    Column(Modifier.padding(14.dp)) {
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
                }
            }

            Spacer(Modifier.height(12.dp))

            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "小念 $versionName · 全本地运行",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalPalette.current.ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "会话只存在这台手机上（SQLite），没有云端副本。\n" +
                            "聊天走 ${if (values.provider == Settings.PROVIDER_DEEPSEEK) "api.deepseek.com" else "zhihui.kaogong.art"}；" +
                            "搜索 / OCR / 语音固定走 DeepSeek 官方接口。",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalPalette.current.inkSecondary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // 底部保存条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
            TextButton(
                onClick = {
                    onSave(values)
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存") }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocalPalette.current.canvas),
    ) { content() }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = LocalPalette.current.divider,
        modifier = Modifier.padding(start = 46.dp),
    )
}

@Composable
private fun CircleIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(LocalPalette.current.canvas)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = LocalPalette.current.ink,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) Color(0xFFD33A3A) else LocalPalette.current.ink,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) Color(0xFFD33A3A) else LocalPalette.current.ink,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalPalette.current.inkSecondary,
            )
            Spacer(Modifier.size(4.dp))
        }
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = Color.Transparent,
            modifier = Modifier.size(14.dp),
        )
    }
}
