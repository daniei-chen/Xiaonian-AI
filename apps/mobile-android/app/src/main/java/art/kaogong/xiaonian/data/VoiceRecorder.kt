package art.kaogong.xiaonian.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

/**
 * 语音输入录音。
 *
 * 直接用 AudioRecord 采 PCM，停止时补 WAV 头再上传 —— DeepSeek ASR 只收
 * 16-bit PCM WAV 或 Ogg Opus，MediaRecorder 默认的 AAC/M4A 传不上去。
 *
 * 16 kHz / 单声道 / 16-bit 是官方支持的采样率，且 8 MiB 上限能录约 4 分钟。
 */
class VoiceRecorder {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    private var running = false

    val isRecording: Boolean get() = running

    @SuppressLint("MissingPermission") // 调用方负责先拿到 RECORD_AUDIO
    fun start(): Boolean {
        if (running) return true

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return false

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            minBuffer * 2,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        pcm.reset()
        rec.startRecording()
        running = true
        record = rec

        worker = thread(name = "voice-record") {
            val buf = ByteArray(4096)
            while (running) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) pcm.write(buf, 0, read)
            }
        }
        return true
    }    /** 停止并返回 WAV 字节；录到的内容太短时返回 null */
    fun stop(): ByteArray? {
        if (!running) return null
        running = false

        worker?.let { runCatching { it.join(800) } }
        worker = null

        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null

        val raw = pcm.toByteArray()
        pcm.reset()
        android.util.Log.d("VoiceRecorder", "stop: pcm=${raw.size} bytes")
        // 少于 0.3 秒（9600 字节）视为误触
        if (raw.size < SAMPLE_RATE * 2 * 3 / 10) return null
        return wrapWav(raw)
    }

    /** 44 字节标准 WAV 头 + PCM 数据 */
    private fun wrapWav(pcmData: ByteArray): ByteArray {
        val total = pcmData.size + 44
        val header = ByteArray(44)
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8

        fun writeAscii(offset: Int, value: String) {
            value.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        }

        fun writeInt(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }

        fun writeShort(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
        }

        writeAscii(0, "RIFF")
        writeInt(4, total - 8)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeInt(16, 16)                       // fmt chunk size
        writeShort(20, 1)                      // PCM
        writeShort(22, channels)
        writeInt(24, SAMPLE_RATE)
        writeInt(28, byteRate)
        writeShort(32, channels * bitsPerSample / 8)
        writeShort(34, bitsPerSample)
        writeAscii(36, "data")
        writeInt(40, pcmData.size)

        return header + pcmData
    }
}
