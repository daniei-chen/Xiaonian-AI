package art.kaogong.xiaonian.data

import android.content.Context

/**
 * 本地设置。
 *
 * 服务器地址必须可改：10.0.2.2 只在模拟器里指向宿主机，真机要填局域网 IP。
 * 这是 P0 阶段唯一必须配置的项，所以用 SharedPreferences 就够了。
 */
object Settings {

    private const val PREFS = "xiaonian"
    private const val KEY_BASE_URL = "base_url"

    /** 模拟器默认值；真机安装后需要在设置里改 */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8788"

    fun baseUrl(context: Context): String {
        val stored = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)

        return stored?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        val normalized = url.trim().trimEnd('/')
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalized.ifBlank { DEFAULT_BASE_URL })
            .apply()
    }
}
