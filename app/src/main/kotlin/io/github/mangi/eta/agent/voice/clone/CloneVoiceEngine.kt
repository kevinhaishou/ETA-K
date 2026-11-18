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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 语音克隆模型（zipvoice zero-shot）：喂一段参考音频 + 文本，用它克隆的音色合成。
 *
 * 只在本机跑，音频与文本都不出设备。参考音频通过 [GenerationConfig.referenceAudio]
 * 逐次传入，所以换音色不需要重建模型，只是合成参数不同。
 *
 * 实例由 [CloneVoiceEngineCache] 共享，而 sherpa 的 OfflineTts 不是线程安全的：
 * 跳段时旧的合成协程还没退出（JNI 阻塞调用不响应协程取消），新协程又会进来。
 * 所以所有合成经 [synthesizeMutex] 串行化，绝不并发进 native。
 */
internal class CloneVoiceEngine private constructor(
    private val tts: OfflineTts,
) : Closeable {
    private val synthesizeMutex = Mutex()

    val sampleRate: Int get() = tts.sampleRate()

    /**
     * 是否有合成正在进行。JNI 调用不响应协程取消，已经被 cancel 的协程可能仍在 native 里跑，
     * 缓存层据此避免在这时释放实例（否则 use-after-free 直接崩）。
     */
    val isBusy: Boolean get() = synthesizeMutex.isLocked

    /**
     * 合成一段语音。[referenceText] 是参考音频里说的话，缺失会明显降低克隆质量；
     * [numSteps] 越大越稳也越慢，distill 模型默认 4 步即可。
     *
     * 实测单段 RTF≈5（4.6 秒音频约 22 秒），所以是挂起函数并切到 Default 线程池，
     * 调用方不要在主线程串行干等。
     */
    suspend fun synthesize(
        text: String,
        reference: FloatArray,
        referenceRate: Int,
        referenceText: String,
        numSteps: Int = 4,
        speed: Float = 1.0f,
    ): FloatArray = synthesizeMutex.withLock {
        withContext(Dispatchers.Default) {
            if (text.isBlank()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有可合成的文本")
            if (reference.isEmpty()) {
                throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "请先导入或录制一段参考音频")
            }
            // zipvoice 是 zero-shot 克隆，参考音频的原文是必需输入。
            // 缺了它 native 不会报错，只会产出空音频，所以必须在这里拦住。
            if (referenceText.isBlank()) {
                throw SpeechFailure(
                    SpeechErrorCode.CONFIGURATION,
                    "请先填写「参考音频里说的话」—— 它必须是这段音频的原文，否则合成的音色会失真甚至没有声音",
                )
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
            tts.generateWithConfig(text, config).samples
        }
    }

    override fun close() {
        runCatching { tts.release() }
    }

    companion object {
        fun open(context: Context, directory: File): CloneVoiceEngine {
            val tokens = requireFile(directory, "tokens.txt")
            val encoder = firstFile(directory, listOf("encoder", "text_encoder"))
            val decoder = firstFile(directory, listOf("decoder"))
            // vocoder 是 sherpa 的硬性必填项（Validate 里 vocoder 为空直接判非法）。
            // distill 包不自带，需要额外放 vocos_24khz.onnx，所以两种命名都要认。
            val vocoder = directory.listFiles()?.firstOrNull {
                it.isFile && (it.name.contains("vocoder", ignoreCase = true) ||
                    it.name.contains("vocos", ignoreCase = true))
            } ?: throw SpeechFailure(
                SpeechErrorCode.CONFIGURATION,
                "模型缺少 vocoder（需要一个 vocos_24khz.onnx）",
            )
            // dataDir 要的是包含 espeak-ng-data 的父目录，传子目录会直接被判为非法配置。
            val dataDir = directory.takeIf { File(it, "espeak-ng-data").isDirectory }
                ?.absolutePath.orEmpty()
            // lexicon 是中文分词的必需项，包里自带 lexicon.txt，之前漏传导致 native 初始化失败。
            val lexicon = File(directory, "lexicon.txt").takeIf { it.isFile }?.absolutePath.orEmpty()
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    zipvoice = OfflineTtsZipVoiceModelConfig(
                        tokens = tokens.absolutePath,
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        vocoder = vocoder.absolutePath,
                        dataDir = dataDir,
                        lexicon = lexicon,
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
