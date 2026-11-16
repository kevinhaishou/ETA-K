package io.github.mangi.eta.agent.voice.offline

import android.content.Context
import android.net.Uri
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import io.github.mangi.eta.agent.voice.SpeechFailure
import io.github.mangi.eta.agent.voice.SpeechErrorCode
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 离线模型引擎：GGUF 走 transcribe.cpp，ONNX 走 sherpa-onnx；两者加载与推理接口完全不同。 */
internal enum class OfflineAsrEngine { ONNX, GGUF }

/**
 * 离线模型条目。体积与内存参考值直接照搬官方离线页，用户据此判断能不能在 8GB 机器上跑。
 */
internal data class OfflineAsrModel(
    val id: String,
    val displayName: String,
    val sizeLabel: String,
    val ramLabel: String,
    val engine: OfflineAsrEngine,
    val engineLabel: String,
    val description: String,
)

internal object OfflineAsrCatalog {
    val models = listOf(
        OfflineAsrModel(
            id = "qwen3-asr-q4km",
            displayName = "Qwen Q4_K_M",
            sizeLabel = "428.6 MB",
            ramLabel = "868 MiB",
            engine = OfflineAsrEngine.GGUF,
            engineLabel = "GGUF Q4_K_M",
            description = "Qwen3-ASR 4 位量化，中英长句更稳，四个量化档里体积最小。",
        ),
        OfflineAsrModel(
            id = "qwen3-asr-q5km",
            displayName = "Qwen Q5_K_M",
            sizeLabel = "495.7 MB",
            ramLabel = "948 MiB",
            engine = OfflineAsrEngine.GGUF,
            engineLabel = "GGUF Q5_K_M",
            description = "Qwen3-ASR 5 位量化，比 Q4 略准，内存占用也更高。",
        ),
        OfflineAsrModel(
            id = "sense-voice-f16",
            displayName = "SenseVoice F16",
            sizeLabel = "442.1 MB",
            ramLabel = "510 MiB",
            engine = OfflineAsrEngine.GGUF,
            engineLabel = "GGUF F16",
            description = "SenseVoice 半精度未量化，准确率最高，占用也最大。",
        ),
        OfflineAsrModel(
            id = "sense-voice-q8",
            displayName = "SenseVoice Q8_0",
            sizeLabel = "253.0 MB",
            ramLabel = "447 MiB",
            engine = OfflineAsrEngine.GGUF,
            engineLabel = "GGUF Q8_0",
            description = "SenseVoice 8 位量化，多语言说完识别；兼顾体积与量化程度。",
        ),
        OfflineAsrModel(
            id = "sense-voice-q4km",
            displayName = "SV Q4_K_M",
            sizeLabel = "209.7 MB",
            ramLabel = "372 MiB",
            engine = OfflineAsrEngine.GGUF,
            engineLabel = "GGUF Q4_K_M",
            description = "SenseVoice 4 位量化，体积最小，嘈杂环境下比 Q8 略差。",
        ),
        OfflineAsrModel(
            id = "sense-voice-int8",
            displayName = "SenseVoice INT8",
            sizeLabel = "227.7 MB",
            ramLabel = "389 MiB",
            engine = OfflineAsrEngine.ONNX,
            engineLabel = "ONNX INT8",
            description = "SenseVoice 整型量化 ONNX，sherpa-onnx 运行时；当前已接通的离线引擎。",
        ),
    )

    fun find(id: String?): OfflineAsrModel? = models.firstOrNull { it.id == id }

    /** 目前只有 ONNX 引擎接通，GGUF 那几档可以先导入，选到会明确提示尚未接入。 */
    fun isRunnable(model: OfflineAsrModel): Boolean = model.engine == OfflineAsrEngine.ONNX
}

internal object OfflineAsrStore {
    private const val ROOT = "offline-asr"

    fun modelDir(context: Context, modelId: String): File =
        File(context.filesDir, "$ROOT/$modelId")

    fun modelFile(context: Context, modelId: String): File? =
        modelDir(context, modelId).listFiles()
            ?.firstOrNull { it.isFile && (it.name.endsWith(".onnx") || it.name.endsWith(".gguf")) }

    fun tokensFile(context: Context, modelId: String): File? =
        modelDir(context, modelId).listFiles()
            ?.firstOrNull { it.isFile && it.name.contains("tokens", ignoreCase = true) }

    fun isReady(context: Context, modelId: String): Boolean {
        if (modelFile(context, modelId) == null) return false
        val model = OfflineAsrCatalog.find(modelId) ?: return false
        return when (model.engine) {
            OfflineAsrEngine.ONNX -> tokensFile(context, modelId) != null
            OfflineAsrEngine.GGUF -> true
        }
    }

    /**
     * 导入用户选中的文件：模型权重与 tokens 一起落盘，同名覆盖。
     *
     * 文件名不做强校验——不同发行包的 tokens 命名不一致，只按扩展名与名字里的 tokens 归类。
     */
    suspend fun import(context: Context, modelId: String, uris: List<Uri>): Int =
        withContext(Dispatchers.IO) {
            val dir = modelDir(context, modelId).apply { mkdirs() }
            var copied = 0
            for (uri in uris) {
                val resolver = context.contentResolver
                val name = queryName(context, uri) ?: continue
                val target = File(dir, name)
                val input = resolver.openInputStream(uri) ?: continue
                input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
                copied++
            }
            if (copied == 0) {
                throw SpeechFailure(SpeechErrorCode.STORAGE, "没有读到可用的模型文件")
            }
            copied
        }

    private fun queryName(context: Context, uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun clear(context: Context, modelId: String) {
        modelDir(context, modelId).deleteRecursively()
    }
}

/**
 * SenseVoice INT8（ONNX）本机识别。整段识别：喂完整波形后一次性解码，不做流式。
 */
internal class SenseVoiceOnnxEngine(
    modelPath: String,
    tokensPath: String,
) : Closeable {
    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelPath,
                    language = "auto",
                    useInverseTextNormalization = true,
                ),
                tokens = tokensPath,
                numThreads = 2,
                provider = "cpu",
            ),
        ),
    )

    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}

/** PCM16 小端单声道转 [-1,1] 浮点，sherpa 的波形输入要求。 */
internal fun pcm16ToFloat(pcm: ByteArray): FloatArray {
    val count = pcm.size / 2
    val out = FloatArray(count)
    for (i in 0 until count) {
        val lo = pcm[i * 2].toInt() and 0xFF
        val hi = pcm[i * 2 + 1].toInt()
        val value = ((hi shl 8) or lo).toShort().toInt()
        out[i] = value / 32768f
    }
    return out
}