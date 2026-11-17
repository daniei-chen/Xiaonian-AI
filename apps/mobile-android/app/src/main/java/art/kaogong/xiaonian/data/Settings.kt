package art.kaogong.xiaonian.data

import android.content.Context

/**
 * 本地配置。
 *
 * 全本地模式：密钥存在手机上，请求直连上游，不经过任何自建服务。
 *
 * 已知代价：密钥在设备里，能被 root 或调试工具读到。个人使用可接受，
 * 要分发给别人就不该这么做（那时应该走服务端代持）。
 * 存法：Private 模式（仅本 app 可读），后续可以换 EncryptedSharedPreferences。
 */
object Settings {

    private const val PREFS = "xiaonian"

    private const val KEY_PROVIDER = "provider"
    private const val KEY_ZHIHUI_KEY = "zhihui_key"
    private const val KEY_DEEPSEEK_KEY = "deepseek_key"
    private const val KEY_MODEL = "model"
    private const val KEY_THINKING = "thinking_enabled"
    private const val KEY_EFFORT = "thinking_effort"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_TTS_RATE = "tts_rate"
    private const val KEY_TTS_VOICE = "tts_voice"
    private const val KEY_ACCENT = "accent_theme"

    /** zhihui：便宜、快，OpenAI 协议；deepseek：官方，支持文件引用与引用编号 */
    const val PROVIDER_ZHIHUI = "zhihui"
    const val PROVIDER_DEEPSEEK = "deepseek"

    const val ZHIHUI_BASE_URL = "https://zhihui.kaogong.art/v1"
    const val ZHIHUI_MODEL = "deepseek-v4.1-flash"
    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    const val DEEPSEEK_MODEL = "deepseek-flash"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun provider(context: Context): String =
        prefs(context).getString(KEY_PROVIDER, null)
            ?.takeIf { it == PROVIDER_ZHIHUI || it == PROVIDER_DEEPSEEK }
            ?: PROVIDER_ZHIHUI

    fun setProvider(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PROVIDER, value).apply()
    }

    fun apiKey(context: Context, provider: String): String {
        val key = if (provider == PROVIDER_DEEPSEEK) KEY_DEEPSEEK_KEY else KEY_ZHIHUI_KEY
        return prefs(context).getString(key, null).orEmpty()
    }

    fun setApiKey(context: Context, provider: String, value: String) {
        val key = if (provider == PROVIDER_DEEPSEEK) KEY_DEEPSEEK_KEY else KEY_ZHIHUI_KEY
        prefs(context).edit().putString(key, value.trim()).apply()
    }

    fun baseUrl(provider: String): String =
        if (provider == PROVIDER_DEEPSEEK) DEEPSEEK_BASE_URL else ZHIHUI_BASE_URL

    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, null)
            ?.takeIf { it.isNotBlank() }
            ?: if (provider(context) == PROVIDER_DEEPSEEK) DEEPSEEK_MODEL else ZHIHUI_MODEL

    fun setModel(context: Context, value: String) {
        prefs(context).edit().putString(KEY_MODEL, value.trim()).apply()
    }

    /** 当前 provider 是否已经配好密钥 */
    fun isConfigured(context: Context): Boolean = apiKey(context, provider(context)).isNotBlank()

    // ---------------------------------------------------------------- 默认行为

    /** 新对话默认是否开启深入思考，省得每次手动点 */
    fun defaultThinking(context: Context): Boolean =
        prefs(context).getBoolean(KEY_THINKING, false)

    fun setDefaultThinking(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_THINKING, value).apply()
    }

    fun defaultEffort(context: Context): String =
        prefs(context).getString(KEY_EFFORT, "high")?.takeIf { it == "high" || it == "max" } ?: "high"

    fun setDefaultEffort(context: Context, value: String) {
        prefs(context).edit().putString(KEY_EFFORT, value).apply()
    }

    /** 回答正文字号倍率：0.9 / 1.0 / 1.15 / 1.3 */
    fun fontScale(context: Context): Float =
        prefs(context).getFloat(KEY_FONT_SCALE, 1f).coerceIn(0.85f, 1.4f)

    fun setFontScale(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_FONT_SCALE, value.coerceIn(0.85f, 1.4f)).apply()
    }

    /** 清除所有配置（不含会话数据） */
    fun clearCredentials(context: Context) {
        prefs(context).edit()
            .remove(KEY_ZHIHUI_KEY)
            .remove(KEY_DEEPSEEK_KEY)
            .apply()
    }

    // ---------------------------------------------------------------- 语音

    /** 朗读语速：本地用 PlaybackParams 调速，不依赖上游参数 */
    fun ttsRate(context: Context): Float =
        prefs(context).getFloat(KEY_TTS_RATE, 1f).coerceIn(0.6f, 1.6f)

    fun setTtsRate(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_TTS_RATE, value.coerceIn(0.6f, 1.6f)).apply()
    }

    /** 音色：上游 voice_id */
    fun ttsVoice(context: Context): String =
        prefs(context).getString(KEY_TTS_VOICE, null)?.takeIf { it.isNotBlank() } ?: "stella"

    fun setTtsVoice(context: Context, value: String) {
        prefs(context).edit().putString(KEY_TTS_VOICE, value.trim()).apply()
    }

    /** 强调色主题：purple / black / green / blue / orange */
    fun accentTheme(context: Context): String =
        prefs(context).getString(KEY_ACCENT, null) ?: "purple"

    fun setAccentTheme(context: Context, id: String) {
        prefs(context).edit().putString(KEY_ACCENT, id).apply()
    }
}
