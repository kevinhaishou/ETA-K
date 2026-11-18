package io.github.mangi.eta.agent.voice.clone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.github.mangi.eta.agent.voice.SpeechErrorCode
import io.github.mangi.eta.agent.voice.SpeechFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 克隆语音的播放：模型输出浮点波形，转 PCM16 后一次性写入音轨。
 *
 * 与朗读用的 SpeechPcmOutput 分开，因为采样率由模型决定（zipvoice 是 24kHz），
 * 不假设固定 24k，避免音调被拉歪。
 */
internal object CloneVoicePlayback {
    suspend fun play(samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "合成结果为空")
        val pcm = ByteArray(samples.size * 2)
        for (index in samples.indices) {
            val value = (samples[index].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[index * 2] = (value and 0xFF).toByte()
            pcm[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
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
            .setBufferSizeInBytes(maxOf(pcm.size, sampleRate * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            // MODE_STATIC 写完即播，等播放头走完再释放，否则尾部会被截断。
            val totalMs = samples.size * 1000L / sampleRate
            Thread.sleep(totalMs + 150)
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}