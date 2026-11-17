package art.kaogong.xiaonian.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import art.kaogong.xiaonian.data.Settings
import art.kaogong.xiaonian.ui.chat.AttachSheet
import art.kaogong.xiaonian.ui.chat.ChatScreen
import art.kaogong.xiaonian.ui.chat.ChatViewModel
import art.kaogong.xiaonian.ui.drawer.XiaonianDrawer
import art.kaogong.xiaonian.ui.settings.SettingsDialog
import art.kaogong.xiaonian.ui.settings.SettingsValues
import art.kaogong.xiaonian.ui.voice.VoiceModeScreen
import art.kaogong.xiaonian.ui.voice.VoicePlayer
import kotlinx.coroutines.launch

@Composable
fun XiaonianApp(
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var voiceMode by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(Settings.provider(context)) }
    var settingsModel by remember { mutableStateOf(Settings.model(context)) }
    var fontScale by remember { mutableStateOf(Settings.fontScale(context)) }
    /** 刚申请权限、授权后要立刻开始录音 */
    var pendingRecord by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingRecord) {
            pendingRecord = false
            viewModel.startRecording()
        } else {
            pendingRecord = false
        }
    }

    // 照片 / 文件：选完立刻上传，拿到 file_id 后挂在待发送列表上
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            readUri(context, uri)?.let { (bytes, name, mime) ->
                viewModel.uploadAttachment(bytes, name, mime)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            readUri(context, uri)?.let { (bytes, name, mime) ->
                viewModel.uploadAttachment(bytes, name, mime)
            }
        }
    }

    /** 拍照：走系统相机写到 cache，回调里直接读文件 */
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = cameraFile
        cameraFile = null
        if (success && file != null && file.exists() && file.length() > 0) {
            runCatching {
                viewModel.uploadAttachment(file.readBytes(), file.name, "image/jpeg")
            }
        }
    }

    val launchCamera: () -> Unit = {
        runCatching {
            val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
            val file = java.io.File.createTempFile("photo-", ".jpg", dir)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            cameraFile = file
            cameraLauncher.launch(uri)
        }.onFailure {
            viewModel.reportError("无法启动相机：${it.message}")
        }
    }

    /** 图片转文字：选完直接 OCR，结果填进输入框 */
    val ocrPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            readUri(context, uri)?.let { (bytes, name, mime) ->
                viewModel.runOcr(bytes, name, mime)
            }
        }
    }

    /** 导出：先拿字节，再弹系统保存框 */
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            readUri(context, uri)?.let { (bytes, _, _) -> viewModel.importHistory(bytes) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportBytes.collect { bytes ->
            pendingExport = bytes
            exportLauncher.launch("xiaonian-history.zip")
        }
    }

    // 转录回来填进输入框，不自动发送
    LaunchedEffect(transcript) {
        transcript?.let { text ->
            input = if (input.isBlank()) text else "$input $text"
            viewModel.consumeTranscript()
        }
    }

    val onVoiceInput: () -> Unit = {
        if (state.recording) {
            viewModel.stopRecording()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                viewModel.startRecording()
            } else {
                pendingRecord = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    if (voiceMode) {
        var speaking by remember { mutableStateOf(false) }
        var showVoiceSettings by remember { mutableStateOf(false) }

        val player = remember {
            VoicePlayer(context).also { p -> p.onComplete = { speaking = false } }
        }

        DisposableEffect(Unit) {
            viewModel.voiceModeActive = true
            onDispose {
                viewModel.voiceModeActive = false
                player.stop()
            }
        }

        LaunchedEffect(Unit) {
            viewModel.voiceAudio.collect { bytes ->
                speaking = bytes.isNotEmpty()
                player.rate = Settings.ttsRate(context)
                player.enqueue(bytes)
            }
        }

        if (showVoiceSettings) {
            art.kaogong.xiaonian.ui.voice.VoiceSettingsDialog(
                rate = Settings.ttsRate(context),
                voice = Settings.ttsVoice(context),
                onDismiss = { showVoiceSettings = false },
                onSave = { rate, voice ->
                    Settings.setTtsRate(context, rate)
                    Settings.setTtsVoice(context, voice)
                    player.rate = rate
                },
            )
        }

        VoiceModeScreen(
            onClose = {
                player.stop()
                speaking = false
                voiceMode = false
            },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            listening = state.recording,
            transcribing = state.transcribing,
            thinking = state.streaming,
            speaking = speaking,
            onAttach = { showAttach = true },
            onStopSpeaking = {
                player.stop()
                speaking = false
            },
            onOpenVoiceSettings = { showVoiceSettings = true },
            onTapOrb = {
                if (state.recording) {
                    viewModel.finishVoiceTurn()
                } else {
                    player.stop()
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.startRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color(0x33000000),
        drawerContent = {
            XiaonianDrawer(
                conversations = state.conversations,
                activeId = state.activeConversationId,
                onSelect = { selected ->
                    viewModel.openConversation(selected.id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.newConversation()
                    input = ""
                    scope.launch { drawerState.close() }
                },
                onExport = {
                    viewModel.exportHistory()
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    showSettings = true
                    scope.launch { drawerState.close() }
                },
                onImport = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
            )
        },
    ) {
        // 字号倍率只作用于回答正文，通过 CompositionLocal 下去
        androidx.compose.runtime.CompositionLocalProvider(
            art.kaogong.xiaonian.ui.theme.LocalFontScale provides fontScale,
        ) {
            ChatScreen(
                state = state,
                modelLabel = settingsModel,
            onOpenSettings = { showSettings = true },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewChat = {
                viewModel.newConversation()
                input = ""
            },
            onConversationClick = { id -> viewModel.openConversation(id) },
            input = input,
            onInputChange = { input = it },
            onSend = {
                viewModel.send(input)
                input = ""
            },
            onStop = { viewModel.stop() },
            onVoiceInput = onVoiceInput,
            onOpenVoiceMode = { voiceMode = true },
            onAttach = { showAttach = true },
            onToggleReasoning = { id -> viewModel.toggleReasoning(id) },
            onRegenerate = { id -> viewModel.regenerate(id) },
            onContinue = { viewModel.continueGeneration() },
            onSpeak = { text -> viewModel.speakMessage(text) },
            onRemoveAttachment = { id -> viewModel.removeAttachment(id) },
            onDismissError = { viewModel.dismissError() },
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            initial = SettingsValues(
                provider = provider,
                apiKey = Settings.apiKey(context, provider),
                model = settingsModel,
                defaultThinking = Settings.defaultThinking(context),
                defaultEffort = Settings.defaultEffort(context),
                fontScale = Settings.fontScale(context),
            ),
            versionName = "0.2.0",
            onDismiss = { showSettings = false },
            onSave = { values ->
                Settings.setProvider(context, values.provider)
                Settings.setApiKey(context, values.provider, values.apiKey)
                Settings.setModel(context, values.model)
                Settings.setDefaultThinking(context, values.defaultThinking)
                Settings.setDefaultEffort(context, values.defaultEffort)
                Settings.setFontScale(context, values.fontScale)
                provider = values.provider
                settingsModel = Settings.model(context)
                fontScale = values.fontScale
                viewModel.applyDefaults(values.defaultThinking, values.defaultEffort)
                viewModel.refreshConversations()
            },
            onTest = { picked, key, callback ->
                viewModel.testUpstream(picked, key, Settings.model(context), callback)
            },
            onClearAll = { viewModel.clearAll() },
        )
    }

    if (showAttach) {
        AttachSheet(
            webSearch = state.webSearch,
            deepSearch = state.deepSearch,
            thinking = state.thinking,
            effort = state.effort,
            onToggleWebSearch = { viewModel.toggleWebSearch() },
            onToggleDeepSearch = { viewModel.toggleDeepSearch() },
            onToggleThinking = { viewModel.toggleThinking() },
            onEffortChange = { viewModel.setEffort(it) },
            onPickImage = {
                showAttach = false
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onTakePhoto = {
                showAttach = false
                launchCamera()
            },
            onPickFile = {
                showAttach = false
                filePicker.launch(arrayOf("*/*"))
            },
            onPickOcr = {
                showAttach = false
                ocrPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onReadLink = {
                showAttach = false
                // 读剪贴板里的链接：用户通常是先复制再来
                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                val text = clip?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
                    ?.trim()

                if (text != null && (text.startsWith("http://") || text.startsWith("https://"))) {
                    viewModel.fetchLink(text)
                } else {
                    viewModel.reportError("剪贴板里没有链接，先复制一个网址")
                }
            },
            onDismiss = { showAttach = false },
        )
    }
}

/** 读 content:// URI：字节 + 文件名 + mime。读不到返回 null */
private fun readUri(context: android.content.Context, uri: android.net.Uri): Triple<ByteArray, String, String>? {
    return runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"

        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: "file"

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        Triple(bytes, name, mime)
    }.getOrNull()
}
