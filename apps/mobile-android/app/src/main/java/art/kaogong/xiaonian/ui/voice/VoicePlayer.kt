package art.kaogong.xiaonian.ui.voice

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/**
 * 语音回答播放器。
 *
 * 走临时文件 + MediaPlayer：TTS 返回的是容器格式（wav/mp3），
 * 用 AudioTrack 播还得自己剥头，不划算。
 */
class VoicePlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var file: File? = null

    /** 播完/被停止时回调，UI 用来复位「正在朗读」的状态 */
    var onComplete: (() -> Unit)? = null

    val isPlaying: Boolean get() = player?.isPlaying == true

    fun play(bytes: ByteArray) {
        stop()
        if (bytes.isEmpty()) return

        val temp = File.createTempFile("tts-", ".wav", context.cacheDir)
        temp.writeBytes(bytes)
        file = temp

        player = runCatching {
            MediaPlayer().apply {
                setDataSource(temp.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                    onComplete?.invoke()
                }
                start()
            }
        }.getOrNull()
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        file?.delete()
        file = null
    }
}
