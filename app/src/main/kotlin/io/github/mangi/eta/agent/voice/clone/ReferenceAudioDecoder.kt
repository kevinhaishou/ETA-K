package io.github.mangi.eta.agent.voice.clone

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.mangi.eta.agent.voice.SpeechErrorCode
import io.github.mangi.eta.agent.voice.SpeechFailure
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 参考音频解码：把用户导入的音频转成模型要的单声道 16kHz 浮点波形。
 *
 * 走系统 MediaExtractor / MediaCodec，支持 mp3 / wav / m4a / flac / ogg 等常见格式；
 * 只取前若干秒——zero-shot 克隆只需要几秒干净的参考说话，过长既无收益也更慢。
 */
internal object ReferenceAudioDecoder {
    private const val TARGET_RATE = 16_000
    private const val MAX_SECONDS = 15

    fun decode(context: Context, uri: android.net.Uri): FloatArray {
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw SpeechFailure(SpeechErrorCode.STORAGE, "无法读取音频文件")
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "文件里没有音频轨道")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "无法识别音频格式")
            val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            val output = ArrayList<Short>()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                val limit = sourceRate * MAX_SECONDS
                while (!outputDone) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buffer = codec.getInputBuffer(inIndex) ?: continue
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            while (shorts.hasRemaining() && output.size < limit * channels) {
                                output.add(shorts.get())
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // 输出格式变化后仍按 16bit PCM 读取，系统解码器统一输出 PCM16。
                    }
                    if (output.size >= limit * channels) outputDone = true
                }
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
            if (output.isEmpty()) {
                throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "音频解码后没有可用样本")
            }
            val mono = toMono(output.toShortArray(), channels)
            return resample(mono, sourceRate, TARGET_RATE)
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun decodeFile(context: Context, file: File): FloatArray =
        decode(context, android.net.Uri.fromFile(file))

    /** 多声道取平均，避免只取左声道导致的音量与相位问题。 */
    private fun toMono(samples: ShortArray, channels: Int): FloatArray {
        if (channels <= 1) return FloatArray(samples.size) { samples[it] / 32768f }
        val frames = samples.size / channels
        return FloatArray(frames) { frame ->
            var sum = 0f
            for (channel in 0 until channels) sum += samples[frame * channels + channel] / 32768f
            sum / channels
        }
    }

    /** 线性插值重采样到 16kHz；参考音频质量比算法复杂度更重要。 */
    private fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate || input.isEmpty()) return input
        val ratio = sourceRate.toDouble() / targetRate
        val size = (input.size / ratio).toInt().coerceAtLeast(1)
        return FloatArray(size) { index ->
            val position = index * ratio
            val left = position.toInt().coerceIn(0, input.size - 1)
            val right = (left + 1).coerceAtMost(input.size - 1)
            val fraction = (position - left).toFloat()
            input[left] * (1 - fraction) + input[right] * fraction
        }
    }
}