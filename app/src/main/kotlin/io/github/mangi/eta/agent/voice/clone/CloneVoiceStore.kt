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
        // vocoder 可选：distill 包把声码器并进了 decoder。
        return hasTokens && hasEncoder && hasDecoder
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

    /**
     * 递归导入选中的目录：模型包里 espeak-ng-data 是子目录，必须整棵复制。
     * 只保留文件名安全的结构，不跟随 .. 之类的路径。
     */
    suspend fun importTree(context: Context, treeUri: Uri): Int = withContext(Dispatchers.IO) {
        val target = modelDir(context).apply { mkdirs() }
        val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        copyTree(context, treeUri, rootId, target)
    }

    private fun copyTree(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String,
        target: File,
    ): Int {
        var copied = 0
        val children = android.provider.DocumentsContract
            .buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1)?.substringAfterLast('/')?.trim().orEmpty()
                val mime = cursor.getString(2).orEmpty()
                if (name.isBlank() || name == "." || name == "..") continue
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    val sub = File(target, name).apply { mkdirs() }
                    copied += copyTree(context, treeUri, id, sub)
                } else {
                    val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    val ok = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            File(target, name).outputStream().use { sink -> input.copyTo(sink) }
                        } != null
                    }.getOrDefault(false)
                    if (ok) copied++
                }
            }
        }
        return copied
    }

    fun clearModel(context: Context) {
        modelDir(context).deleteRecursively()
    }

    const val SAMPLE_RATE = 16_000
}