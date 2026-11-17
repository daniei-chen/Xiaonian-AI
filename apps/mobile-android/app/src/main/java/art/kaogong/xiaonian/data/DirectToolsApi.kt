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

/** 一条搜索命中 */
data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
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

    suspend fun ocr(bytes: ByteArray, filename: String, mime: String): String =
        withContext(Dispatchers.IO) {
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("purpose", "text_recognition")
                .addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
                .build()

            val request = authBuilder("/v1/tools/ocr").post(form).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("识别失败 HTTP ${response.code}")
                val text = response.body?.string().orEmpty()
                val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: throw IOException("识别返回无法解析")

                val pages = root["pages"] as? JsonArray ?: return@use ""
                pages.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
                    .joinToString("\n\n")
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
