package io.github.mangi.eta.agent.voice.clone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.github.mangi.eta.agent.voice.SpeechErrorCode
import io.github.mangi.eta.agent.voice.SpeechFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 克隆语音的播放：模型输出浮点波形，转 PCM16 后按块写入音轨。
 *
 * 和朗读用的 SpeechPcmOutput 分开，因为采样率由模型决定（zipvoice 是 24kHz），
 * 不假设固定 24k，避免音调被拉歪。
 *
 * 用 MODE_STREAM 分块写而不是 MODE_STATIC 一次写完整段：
 * 一段 500 字中文的 24kHz PCM16 约 5MB，静态缓冲有平台上限，长段落会直接创建失败；
 * 流式还能按 [CHUNK_MS] 粒度响应暂停与跳段，不必等整段放完。
 */
internal object CloneVoicePlayback {
    /** 每次写入的音频长度：越短暂停/跳段响应越快。 */
    private const val CHUNK_MS = 100
    /** 暂停等待时的轮询间隔。 */
    private const val POLL_MS = 50L

    suspend fun play(
        samples: FloatArray,
        sampleRate: Int,
        shouldPause: () -> Boolean = { false },
        shouldStop: () -> Boolean = { false },
    ) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "合成结果为空")
        val pcm = ByteArray(samples.size * 2)
        for (index in samples.indices) {
            val value = (samples[index].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[index * 2] = (value and 0xFF).toByte()
            pcm[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }

        val chunkBytes = maxOf(sampleRate * 2 * CHUNK_MS / 1000, 2048)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            // 留两块余量，减少 write 等待硬件消费的次数。
            .setBufferSizeInBytes(chunkBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.play()
            var offset = 0
            var pausedNow = false
            while (offset < pcm.size) {
                if (shouldStop()) return@withContext
                val wantPause = shouldPause()
                if (wantPause != pausedNow) {
                    if (wantPause) track.pause() else track.play()
                    pausedNow = wantPause
                }
                if (pausedNow) {
                    // 暂停时不写，否则缓冲区塞满后 write 会阻塞，跳段反应也变迟钝。
                    delay(POLL_MS)
                    continue
                }
                val size = minOf(chunkBytes, pcm.size - offset)
                val written = track.write(pcm, offset, size)
                // write 返回非正数是音轨错误（如 DEAD_OBJECT），直接收工，
                // 否则下面按「整段还没播」算出的等待会凭空拖很久。
                if (written <= 0) return@withContext
                offset += written
            }
            if (shouldStop()) return@withContext
            // 写完只是进了缓冲区，还要等缓冲里的余量真正放完，否则尾巴被切。
            // 缓冲容量是两块，所以余量至少按 2 块算。
            val remainingBytes = (pcm.size - offset).toLong()
            val pendingMs = maxOf(remainingBytes, chunkBytes * 2L) * 1000L / (sampleRate * 2L)
            delay(pendingMs + 150L)
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}
