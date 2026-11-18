package io.github.mangi.eta.agent.voice.clone

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 语音克隆的模型目录与参考音频存放。
 *
 * 模型走「文件夹导入」：用户在系统选择器里选中解压好的目录（含 tokens.txt、
 * encoder/decoder/vocoder 的 onnx），这里把它们复制进应用私有目录。
 * Android 没有内置 tar.bz2 解压，与其引入解压库，不如让用户交给文件管理器解开。
 */
internal object CloneVoiceStore {
    private const val MODEL_ROOT = "voice-clone/model"
    private const val REFERENCE_FILE = "voice-clone/reference.pcm"
    private const val REFERENCE_TEXT = "voice-clone/reference.txt"

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_ROOT)

    fun referenceFile(context: Context): File = File(context.filesDir, REFERENCE_FILE)

    private fun referenceTextFile(context: Context): File = File(context.filesDir, REFERENCE_TEXT)

    /** 模型是否就位：tokens + 三个 onnx 缺一不可。 */
    fun isModelReady(context: Context): Boolean {
        val dir = modelDir(context)
        if (!dir.isDirectory) return false
        val names = dir.listFiles()?.filter { it.isFile }?.map { it.name.lowercase() }.orEmpty()
        val hasTokens = names.any { it.contains("tokens") }
        val hasEncoder = names.any { it.contains("encoder") }
        val hasDecoder = names.any { it.contains("decoder") }
        val hasVocoder = names.any { it.contains("vocoder") }
        return hasTokens && hasEncoder && hasDecoder && hasVocoder
    }

    fun hasReference(context: Context): Boolean = referenceFile(context).length() > 0

    fun referenceText(context: Context): String =
        referenceTextFile(context).takeIf { it.isFile }?.readText().orEmpty().trim()

    fun saveReferenceText(context: Context, text: String) {
        referenceTextFile(context).apply {
            parentFile?.mkdirs()
            writeText(text.trim())
        }
    }

    /**
     * 导入参考音频：解码成 16kHz 单声道后存成裸 PCM，避免每次合成重复解码。
     * 返回采样点数与秒数，供界面显示。
     */
    suspend fun importReference(context: Context, uri: Uri): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val samples = ReferenceAudioDecoder.decode(context, uri)
        writeReference(context, samples)
        samples.size to (samples.size / SAMPLE_RATE)
    }

    suspend fun saveRecordedReference(context: Context, pcm16: ByteArray): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            val floats = FloatArray(pcm16.size / 2) { index ->
                val lo = pcm16[index * 2].toInt() and 0xFF
                val hi = pcm16[index * 2 + 1].toInt()
                ((hi shl 8) or lo).toShort().toInt() / 32768f
            }
            writeReference(context, floats)
            floats.size to (floats.size / SAMPLE_RATE)
        }

    fun loadReference(context: Context): FloatArray {
        val file = referenceFile(context)
        if (!file.isFile) return FloatArray(0)
        val bytes = file.readBytes()
        return FloatArray(bytes.size / 4) { index ->
            val base = index * 4
            java.nio.ByteBuffer.wrap(bytes, base, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).float
        }
    }

    private fun writeReference(context: Context, samples: FloatArray) {
        val file = referenceFile(context)
        file.parentFile?.mkdirs()
        val buffer = java.nio.ByteBuffer.allocate(samples.size * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putFloat(it) }
        file.writeBytes(buffer.array())
    }

    /** 从选中的目录里复制模型文件；同名覆盖。 */
    suspend fun importModelDir(context: Context, treeUri: Uri, names: List<String>): Int =
        withContext(Dispatchers.IO) {
            val target = modelDir(context).apply { mkdirs() }
            var copied = 0
            for (name in names) {
                val cleaned = name.substringAfterLast('/')
                if (cleaned.isBlank() || cleaned.contains("..")) continue
                val uri = Uri.withAppendedPath(treeUri, name)
                val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                    ?: continue
                input.use { source ->
                    File(target, cleaned).outputStream().use { sink -> source.copyTo(sink) }
                }
                copied++
            }
            copied
        }

    fun clearModel(context: Context) {
        modelDir(context).deleteRecursively()
    }

    const val SAMPLE_RATE = 16_000
}