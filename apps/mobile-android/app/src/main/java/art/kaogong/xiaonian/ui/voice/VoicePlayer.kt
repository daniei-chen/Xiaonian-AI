package art.kaogong.xiaonian.ui.voice

import android.content.Context
import android.media.MediaPlayer
import java.io.File

/**
 * 语音回答播放器（队列版）。
 *
 * TTS 按句合成，一句一个音频片段。这里排成队列顺序播放，
 * 这样第一句合成完就能出声，不用等整段。
 */
class VoicePlayer(private val context: Context) {

    private val queue = ArrayDeque<ByteArray>()
    private var player: MediaPlayer? = null
    private var file: File? = null
    private var draining = false

    /** 播放速度倍率，由设置页控制 */
    var rate: Float = 1f

    /** 全部播放结束（或被 stop）时回调，UI 用来复位「正在朗读」 */
    var onComplete: (() -> Unit)? = null

    val isPlaying: Boolean get() = player?.isPlaying == true

    /** 追加一段音频；空闲时立刻开始播 */
    fun enqueue(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        queue.addLast(bytes)
        if (!draining) playNext()
    }

    private fun playNext() {
        val bytes = queue.removeFirstOrNull()
        if (bytes == null) {
            draining = false
            player = null
            file?.delete()
            file = null
            onComplete?.invoke()
            return
        }

        draining = true
        val temp = File.createTempFile("tts-", ".wav", context.cacheDir)
        temp.writeBytes(bytes)
        file = temp

        val created = runCatching {
            MediaPlayer().apply {
                setDataSource(temp.absolutePath)
                prepare()
                // 语速在本地调，不用依赖上游参数
                if (rate != 1f) {
                    runCatching {
                        playbackParams = playbackParams.setSpeed(rate)
                    }
                }
                setOnCompletionListener {
                    runCatching { it.release() }
                    playNext()
                }
                start()
            }
        }.getOrNull()

        if (created == null) {
            // 这一段坏了就跳过，不要让整条队列卡死
            playNext()
        } else {
            player = created
        }
    }

    fun stop() {
        queue.clear()
        draining = false
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        file?.delete()
        file = null
        onComplete?.invoke()
    }
}
