package art.kaogong.xiaonian.data

/**
 * 模拟器里 10.0.2.2 指向宿主机。真机调试改成局域网 IP。
 * 接真实环境时改这里，或者接到 BuildConfig。 
 */
object ApiConfig {
    const val BASE_URL: String = "http://10.0.2.2:8788"
}
