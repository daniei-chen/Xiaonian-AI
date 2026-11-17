package art.kaogong.xiaonian.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 一条搜索命中；authority/score 用来决定抓哪几篇正文 */
data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val authority: Int = 0,
    val score: Double = 0.0,
)

/**
 * 工具能力：搜索 / 读链接 / OCR / 语音 / 文件上传。
 *
 * 全部走 DeepSeek 官方接口（`v1/tools/` 下的端点只有官方提供），所以固定用
 * DEEPSEEK 那把 key；聊天可以用更便宜的 zhihui，两者互不影响。
 */
class DirectToolsApi(private val keyProvider: () -> String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val base = Settings.DEEPSEEK_BASE_URL

    private fun authBuilder(path: String): Request.Builder =
        Request.Builder()
            .url("${base.trimEnd('/')}$path")
            .header("Authorization", "Bearer ${keyProvider()}")

    // ---------------------------------------------------------------- 搜索

    suspend fun search(query: String, count: Int = 8): List<SearchHit> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", query)
            put("count", count)
        }

        val request = authBuilder("/v1/tools/search")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("搜索失败 HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw IOException("搜索返回无法解析")

            val results = root["results"] as? JsonArray ?: return@use emptyList()
            results.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val summary = item["snippet"]?.jsonPrimitive?.contentOrNull
                    ?: item["summary"]?.jsonPrimitive?.contentOrNull.orEmpty()
                SearchHit(
                    title = item["title"]?.jsonPrimitive?.contentOrNull ?: url,
                    url = url,
                    snippet = summary.take(400),
                    authority = item["authority"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    score = item["rerank_score"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0,
                )
            }
        }
    }

    // ---------------------------------------------------------------- 读链接

    suspend fun fetchPage(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("url", url) }
        val request = authBuilder("/v1/tools/fetch")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("读取失败 HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw IOException("读取返回无法解析")
            val content = root["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val title = root["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            title.ifBlank { url } to content
        }
    }

    // ---------------------------------------------------------------- OCR

    suspend fun ocr(
        bytes: ByteArray,
        filename: String,
        mime: String,
        /** true 时用版面识别并还原阅读顺序（表格/多栏文档） */
        layout: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "purpose",
                if (layout) "rich_layout_recognition" else "text_recognition",
            )
            .addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
            .build()

        val request = authBuilder("/v1/tools/ocr").post(form).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("识别失败 HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw IOException("识别返回无法解析")

            val pages = root["pages"] as? JsonArray ?: return@use ""
            val raw = pages.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            if (layout) Layout.reorderByLayout(raw.joinToString("\n")) else raw.joinToString("\n\n")
        }
    }

    /** 版面解析单独放一个嵌套对象，避免和切句的 companion 撞名 */
    private object Layout {
        /** `<|ref|>文字<|/ref|><|box|>[[x1,y1,x2,y2]]<|/box|> 内容` */
        private val LAYOUT_LINE = Regex(
            """<\|ref\|>[^<]*<\|/ref\|><\|box\|>\[\[(\d+),(\d+),(\d+),(\d+)\]\]<\|/box\|>\s*(.+?)(?=<\|ref\||$)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        /**
         * 版面识别返回的是带坐标的碎片，顺序不一定等于阅读顺序。
         * 按 y 坐标聚成行、行内按 x 排序，就还原成正常阅读顺序——
         * 多栏和表格至少不会再把两栏串在一起。
         */
        fun reorderByLayout(raw: String): String {
            val items = LAYOUT_LINE.findAll(raw).mapNotNull { m ->
                val x1 = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val y1 = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val body = m.groupValues[5].trim()
                if (body.isEmpty()) return@mapNotNull null
                body to (x1 to y1)
            }.toList()

            if (items.isEmpty()) {
                // 没有坐标标记说明这页没解析出版面，退回原文
                return raw.replace(Regex("<\\|[^|]+\\|>"), "").trim()
            }

            // 同一行的判定：y 差值小于行高估算（这里用 18 像素的容差）
            val tolerance = 18
            val sorted = items.sortedBy { it.second.second }
            val lines = mutableListOf<MutableList<Pair<String, Int>>>()

            var currentY = sorted.first().second.second
            var currentLine = mutableListOf<Pair<String, Int>>()

            for ((body, pos) in sorted) {
                if (pos.second - currentY > tolerance) {
                    lines.add(currentLine)
                    currentLine = mutableListOf<Pair<String, Int>>()
                    currentY = pos.second
                }
                currentLine.add(body to pos.first)
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)

            return lines.joinToString("\n") { line ->
                line.sortedBy { it.second }.joinToString(" ") { it.first }
            }.trim()
        }
    }

    // ---------------------------------------------------------------- 语音识别

    suspend fun transcribe(bytes: ByteArray, filename: String, mime: String): String =
        withContext(Dispatchers.IO) {
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
                .build()

            val request = authBuilder("/v1/tools/asr").post(form).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("识别失败 HTTP ${response.code}")
                val text = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                root?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
            }
        }

    // ---------------------------------------------------------------- 语音合成

    suspend fun synthesize(text: String, voice: String = "stella"): ByteArray =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("text", text.take(4000))
                put("voice_id", voice)
                put("format", "wav")
            }

            val request = authBuilder("/v1/tools/tts")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("合成失败 HTTP ${response.code}")
                response.body?.bytes() ?: ByteArray(0)
            }
        }

    /**
     * 按句合成：把长文本切段，每段合成完立刻交给调用方播放。
     *
     * 整段合成的体验很差——一个 800 字的回答要等十几秒才有声音。切句之后
     * 第一句出来就能播，后面的在旁边合成。代价是多几次请求，但 TTS 便宜。
     */
    suspend fun synthesizeBySentence(
        text: String,
        voice: String = "stella",
        onClip: suspend (text: String, audio: ByteArray) -> Unit,
    ) = withContext(Dispatchers.IO) {
        for (sentence in splitForSpeech(text)) {
            val audio = runCatching { synthesize(sentence, voice) }.getOrNull()
            if (audio != null && audio.isNotEmpty()) onClip(sentence, audio)
        }
    }

    companion object {
        /**
         * 切句规则：句末标点（中英）后断，长度上限 90 字，超长再按逗号切。
         * 太短的碎片（<4 字）并到前一句，否则语气会很碎。
         */
        fun splitForSpeech(text: String): List<String> {
            val normalized = text.replace(Regex("[\\r\\n]+"), " ").trim()
            if (normalized.isEmpty()) return emptyList()

            val rough = Regex("(?<=[。！？!?；;])|(?<=[.:])\\s+")
                .split(normalized)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val merged = mutableListOf<String>()
            for (piece in rough) {
                val last = merged.lastOrNull()
                if (last != null && (last.length < 4 || last.length + piece.length <= 90)) {
                    // 短碎片并回上一句；或合并后仍不超长
                    if (last.length + piece.length <= 90) {
                        merged[merged.lastIndex] = last + piece
                        continue
                    }
                }
                if (piece.length <= 90) {
                    merged.add(piece)
                } else {
                    // 太长就按逗号再切
                    Regex("(?<=[，,、])").split(piece)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { merged.add(it) }
                }
            }
            return merged
        }
    }

    // ---------------------------------------------------------------- 文件上传

    suspend fun upload(bytes: ByteArray, filename: String, mime: String): FileRef =
        withContext(Dispatchers.IO) {
            // 图片不能带 purpose，PDF/Word/PPT 要 rich_layout_recognition，其余 user_data
            val purpose = when {
                mime.startsWith("image/") -> null
                mime.contains("pdf") || mime.contains("word") ||
                    mime.contains("presentation") || mime.contains("powerpoint") ->
                    "rich_layout_recognition"
                else -> "user_data"
            }

            val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            if (purpose != null) formBuilder.addFormDataPart("purpose", purpose)
            formBuilder.addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))

            val request = Request.Builder()
                .url("${base.trimEnd('/')}/anthropic/v1/files")
                .post(formBuilder.build())
                .header("x-api-key", keyProvider())
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "files-api-2025-04-14")
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("上传失败 HTTP ${response.code}: ${text.take(120)}")
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: throw IOException("上传返回无法解析")
                val id = root["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw IOException("上传未返回 file id")

                FileRef(
                    fileId = id,
                    name = root["filename"]?.jsonPrimitive?.contentOrNull ?: filename,
                    mime = root["mime_type"]?.jsonPrimitive?.contentOrNull ?: mime,
                    size = root["size_bytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?: bytes.size.toLong(),
                    kind = if (mime.startsWith("image/")) "image" else "file",
                )
            }
        }
}
