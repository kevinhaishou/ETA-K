package io.github.mangi.eta.agent.voice.clone

import android.content.Context
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import io.github.mangi.eta.agent.voice.SpeechErrorCode
import io.github.mangi.eta.agent.voice.SpeechFailure
import java.io.Closeable
import java.io.File

/**
 * 语音克隆模型（zipvoice zero-shot）：喂一段参考音频 + 文本，用它克隆的音色合成。
 *
 * 只在本机跑，音频与文本都不出设备。参考音频通过 [GenerationConfig.referenceAudio]
 * 逐次传入，所以换音色不需要重建模型，只是合成参数不同。
 */
internal class CloneVoiceEngine private constructor(
    private val tts: OfflineTts,
) : Closeable {
    val sampleRate: Int get() = tts.sampleRate()

    /**
     * 合成一段语音。[referenceText] 是参考音频里说的话，缺失会明显降低克隆质量；
     * [numSteps] 越大越稳也越慢，distill 模型默认 4 步即可。
     */
    fun synthesize(
        text: String,
        reference: FloatArray,
        referenceRate: Int,
        referenceText: String,
        numSteps: Int = 4,
        speed: Float = 1.0f,
    ): FloatArray {
        if (text.isBlank()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有可合成的文本")
        if (reference.isEmpty()) {
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "请先导入或录制一段参考音频")
        }
        val config = GenerationConfig(
            silenceScale = 0.2f,
            speed = speed,
            sid = 0,
            referenceAudio = reference,
            referenceSampleRate = referenceRate,
            referenceText = referenceText,
            numSteps = numSteps,
        )
        val audio = tts.generateWithConfig(text, config)
        return audio.samples
    }

    override fun close() {
        runCatching { tts.release() }
    }

    companion object {
        fun open(context: Context, directory: File): CloneVoiceEngine {
            val tokens = requireFile(directory, "tokens.txt")
            val encoder = firstFile(directory, listOf("encoder", "text_encoder"))
            val decoder = firstFile(directory, listOf("decoder"))
            val vocoder = firstFile(directory, listOf("vocoder"))
            val dataDir = File(directory, "espeak-ng-data").takeIf { it.isDirectory }?.absolutePath.orEmpty()
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    zipvoice = OfflineTtsZipVoiceModelConfig(
                        tokens = tokens.absolutePath,
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        vocoder = vocoder.absolutePath,
                        dataDir = dataDir,
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
            )
            return try {
                CloneVoiceEngine(OfflineTts(assetManager = null, config = config))
            } catch (error: Throwable) {
                throw SpeechFailure(
                    SpeechErrorCode.CONFIGURATION,
                    "语音克隆模型加载失败：" + (error.message ?: error.javaClass.simpleName),
                )
            }
        }

        private fun requireFile(directory: File, name: String): File =
            File(directory, name).takeIf { it.isFile }
                ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "模型缺少 $name")

        private fun firstFile(directory: File, keywords: List<String>): File =
            directory.listFiles()?.firstOrNull { file ->
                file.isFile && keywords.any { file.name.contains(it, ignoreCase = true) }
            } ?: throw SpeechFailure(
                SpeechErrorCode.CONFIGURATION,
                "模型缺少 " + keywords.first() + " 相关文件",
            )
    }
}