package io.github.mangi.eta.data.repository

import android.content.Context
import android.util.AtomicFile
import io.github.mangi.eta.data.datastore.SettingsDataStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

internal data class AgentMemorySnapshot(
    val content: String,
    val revision: String,
    val byteSize: Int,
    val lineCount: Int,
)

internal data class AgentMemoryReadResult(
    val snapshot: AgentMemorySnapshot,
    val content: String,
    val startLine: Int?,
    val endLine: Int?,
    val hasMore: Boolean,
    val matchedLines: Int,
)

internal sealed interface AgentMemoryMutation {
    val revision: String

    data class ReplaceRange(
        override val revision: String,
        val startLine: Int,
        val endLine: Int,
        val content: String,
    ) : AgentMemoryMutation

    data class Append(
        override val revision: String,
        val content: String,
    ) : AgentMemoryMutation

    data class Clear(
        override val revision: String,
    ) : AgentMemoryMutation
}

internal sealed interface AgentMemoryWriteResult {
    data class Success(val snapshot: AgentMemorySnapshot) : AgentMemoryWriteResult
    data class Conflict(val snapshot: AgentMemorySnapshot) : AgentMemoryWriteResult
}

internal class AgentMemoryException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** 单一 MEMORY.md 的有界、原子文件存储。 */
internal class AgentMemoryStore(
    rootDir: File,
) {
    private val memoryDir = File(rootDir, DIRECTORY_NAME)
    private val atomicFile = AtomicFile(File(memoryDir, FILE_NAME))
    private val lock = Any()

    fun snapshot(): AgentMemorySnapshot = synchronized(lock) {
        snapshotLocked()
    }

    fun read(
        query: String? = null,
        startLine: Int = DEFAULT_START_LINE,
        maxChars: Int = DEFAULT_READ_CHARS,
    ): AgentMemoryReadResult = synchronized(lock) {
        val snapshot = snapshotLocked()
        val boundedChars = maxChars.coerceIn(MIN_READ_CHARS, MAX_READ_CHARS)
        if (query.isNullOrBlank()) {
            page(snapshot, startLine, boundedChars)
        } else {
            search(snapshot, query.trim(), boundedChars)
        }
    }

    fun mutate(mutation: AgentMemoryMutation): AgentMemoryWriteResult = synchronized(lock) {
        val current = snapshotLocked()
        if (mutation.revision != current.revision) {
            return@synchronized AgentMemoryWriteResult.Conflict(current)
        }
        val updated = when (mutation) {
            is AgentMemoryMutation.ReplaceRange -> replaceRange(current, mutation)
            is AgentMemoryMutation.Append -> append(current, mutation.content)
            is AgentMemoryMutation.Clear -> ""
        }
        writeLocked(updated)
        AgentMemoryWriteResult.Success(snapshotOf(updated))
    }

    fun replaceAll(content: String): AgentMemorySnapshot = synchronized(lock) {
        writeLocked(content)
        snapshotOf(content)
    }

    fun replaceAllIfRevision(content: String, revision: String): AgentMemoryWriteResult = synchronized(lock) {
        val current = snapshotLocked()
        if (current.revision != revision) return@synchronized AgentMemoryWriteResult.Conflict(current)
        writeLocked(content)
        AgentMemoryWriteResult.Success(snapshotOf(content))
    }

    private fun snapshotLocked(): AgentMemorySnapshot {
        val file = atomicFile.baseFile
        if (!file.exists()) return snapshotOf("")
        val bytes = try {
            atomicFile.openRead().use { it.readBytes() }
        } catch (throwable: IOException) {
            throw AgentMemoryException(
                code = "MEMORY_READ_FAILED",
                message = "无法读取记忆文件",
                cause = throwable,
            )
        }
        if (bytes.size > MAX_FILE_BYTES) {
            throw AgentMemoryException(
                code = "MEMORY_TOO_LARGE",
                message = "记忆文件超过 1 MiB 安全上限",
            )
        }
        val content = bytes.toString(Charsets.UTF_8)
        return snapshotOf(content, bytes)
    }

    private fun writeLocked(content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FILE_BYTES) {
            throw AgentMemoryException(
                code = "MEMORY_TOO_LARGE",
                message = "记忆文件不能超过 1 MiB UTF-8 字节",
            )
        }
        if (!memoryDir.exists() && !memoryDir.mkdirs() && !memoryDir.isDirectory) {
            throw AgentMemoryException(
                code = "MEMORY_WRITE_FAILED",
                message = "无法创建记忆目录",
            )
        }
        val output = try {
            atomicFile.startWrite()
        } catch (throwable: IOException) {
            throw AgentMemoryException(
                code = "MEMORY_WRITE_FAILED",
                message = "无法开始写入记忆文件",
                cause = throwable,
            )
        }
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (throwable: Throwable) {
            atomicFile.failWrite(output)
            throw AgentMemoryException(
                code = "MEMORY_WRITE_FAILED",
                message = "无法保存记忆文件",
                cause = throwable,
            )
        }
    }

    private fun replaceRange(
        snapshot: AgentMemorySnapshot,
        mutation: AgentMemoryMutation.ReplaceRange,
    ): String {
        val lines = snapshot.content.memoryLines().toMutableList()
        if (
            mutation.startLine < 1 ||
            mutation.endLine < mutation.startLine ||
            mutation.endLine > lines.size
        ) {
            throw AgentMemoryException(
                code = "MEMORY_RANGE_INVALID",
                message = "替换行范围无效；请重新读取记忆后再试",
            )
        }
        val replacement = mutation.content.memoryLines()
        lines.subList(mutation.startLine - 1, mutation.endLine).clear()
        if (replacement.isNotEmpty()) {
            lines.addAll(mutation.startLine - 1, replacement)
        }
        return lines.joinToString("\n")
    }

    private fun append(snapshot: AgentMemorySnapshot, content: String): String {
        if (content.isEmpty()) return snapshot.content
        if (snapshot.content.isEmpty()) return content
        return snapshot.content.trimEnd('\n') + "\n" + content
    }

    private fun page(
        snapshot: AgentMemorySnapshot,
        requestedStartLine: Int,
        maxChars: Int,
    ): AgentMemoryReadResult {
        val lines = snapshot.content.memoryLines()
        if (lines.isEmpty()) {
            return AgentMemoryReadResult(snapshot, "", null, null, false, 0)
        }
        val startIndex = (requestedStartLine - 1).coerceIn(0, lines.size)
        if (startIndex >= lines.size) {
            return AgentMemoryReadResult(snapshot, "", null, null, false, 0)
        }
        val output = StringBuilder()
        var endIndex = startIndex
        while (endIndex < lines.size) {
            val rendered = "${endIndex + 1}: ${lines[endIndex]}"
            val separatorLength = if (output.isEmpty()) 0 else 1
            if (output.isNotEmpty() && output.length + separatorLength + rendered.length > maxChars) break
            if (output.isNotEmpty()) output.append('\n')
            output.append(rendered.take(maxChars - output.length))
            endIndex++
            if (output.length >= maxChars) break
        }
        return AgentMemoryReadResult(
            snapshot = snapshot,
            content = output.toString(),
            startLine = startIndex + 1,
            endLine = endIndex,
            hasMore = endIndex < lines.size,
            matchedLines = endIndex - startIndex,
        )
    }

    /**
     * 关键词检索：先按相关度给每行打分，预算不足时优先保住强匹配行，最终仍按行号顺序渲染。
     *
     * 原来的两处短板都在这里补掉：
     * 1. 只做单串 contains，换个说法（记忆写「偏好深色」、问「上次那个深色主题」）就搜不到 —— 现在按词打分；
     * 2. 命中行按行号顺序吃预算，前排弱匹配会把后面的强匹配挤出输出 —— 现在按分数抢占预算。
     */
    private fun search(
        snapshot: AgentMemorySnapshot,
        query: String,
        maxChars: Int,
    ): AgentMemoryReadResult {
        val lines = snapshot.content.memoryLines()
        val tokens = searchTokens(query)
        val scored = ArrayList<Pair<Int, Int>>()
        for (index in lines.indices) {
            val score = lineScore(lines[index], query, tokens)
            if (score > 0) scored.add(index to score)
        }
        // 相关度优先（同分保持行号顺序），下面按这个顺序争抢字符预算。
        scored.sortWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
        val included = sortedSetOf<Int>()
        var budget = maxChars
        for ((index, _) in scored) {
            var gained = 0
            var added = 0
            for (candidate in (index - SEARCH_CONTEXT_LINES)..(index + SEARCH_CONTEXT_LINES)) {
                if (candidate in lines.indices && candidate !in included) {
                    gained += lines[candidate].length + LINE_RENDER_OVERHEAD
                    added++
                }
            }
            // 第一条命中无论如何保留，避免首行超长时直接返回空结果。
            if (added > 0 && included.isNotEmpty() && gained > budget) break
            for (candidate in (index - SEARCH_CONTEXT_LINES)..(index + SEARCH_CONTEXT_LINES)) {
                if (candidate in lines.indices) included.add(candidate)
            }
            budget = (budget - gained).coerceAtLeast(0)
        }
        val output = StringBuilder()
        var lastIncluded: Int? = null
        var renderedCount = 0
        val today = java.time.LocalDate.now()
        for (index in included) {
            // 行内日期距今超过阈值时明确标注，让模型自己权衡这条记忆还可不可信。
            val stale = staleNote(lines[index], today)?.let { " ⚠️ $it" }.orEmpty()
            val rendered = "${index + 1}: ${lines[index]}$stale"
            val gap = when {
                output.isEmpty() -> ""
                lastIncluded != null && index > lastIncluded + 1 -> "\n…\n"
                else -> "\n"
            }
            if (output.isNotEmpty() && output.length + gap.length + rendered.length > maxChars) break
            output.append(gap).append(rendered.take(maxChars - output.length - gap.length))
            lastIncluded = index
            renderedCount++
            if (output.length >= maxChars) break
        }
        return AgentMemoryReadResult(
            snapshot = snapshot,
            content = output.toString(),
            startLine = included.firstOrNull()?.plus(1),
            endLine = lastIncluded?.plus(1),
            hasMore = renderedCount < included.size,
            matchedLines = scored.size,
        )
    }

    /** 把查询拆成检索词：空格/常见中英标点分词；中文无空格时整串仍作为一个词。 */
    private fun searchTokens(query: String): List<String> {
        val parts = query
            .split(' ', '\t', '\n', '，', '、', ',', '。', '；', ';', '：', ':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> listOf(query.trim())
            parts.size == 1 -> parts
            else -> parts.distinct()
        }
    }

    /**
     * 单行相关度打分。纯本地加权，不依赖模型、embedding 或网络：
     * 整串命中 > 命中词数量 > 同词重复出现 > 命中全部词 > 结构化条目（标题/列表项）轻微加权。
     */
    private fun lineScore(line: String, query: String, tokens: List<String>): Int {
        if (line.isBlank()) return 0
        val haystack = line.lowercase()
        var score = 0
        if (haystack.contains(query.lowercase())) score += SCORE_EXACT_PHRASE
        var hits = 0
        for (token in tokens) {
            val needle = token.lowercase()
            if (needle.isEmpty()) continue
            if (!haystack.contains(needle)) continue
            hits++
            score += SCORE_TOKEN_HIT
            var occurrences = 0
            var from = 0
            while (occurrences <= SCORE_MAX_OCCURRENCES) {
                val at = haystack.indexOf(needle, from)
                if (at < 0) break
                occurrences++
                from = at + needle.length
            }
            if (occurrences > 1) {
                score += (occurrences - 1).coerceAtMost(SCORE_MAX_OCCURRENCES) * SCORE_REPEAT_BONUS
            }
        }
        if (hits == 0 && score == 0) return 0
        if (tokens.size > 1 && hits == tokens.size) score += SCORE_ALL_TOKENS
        // 记忆里的标题行与列表项通常是一条完整事实，命中时比正文行更值得返回。
        val first = line.trimStart().firstOrNull()
        if (first == '#' || first == '-' || first == '*' || first?.isDigit() == true) {
            score += SCORE_STRUCTURED_LINE
        }
        return score
    }

    /** 提取行内首个 YYYY-MM-DD 日期；没有返回 null。记忆条目写作时应带记录日期。 */
    private fun lineDate(line: String): java.time.LocalDate? =
        DATE_PATTERN.find(line)?.let { match ->
            runCatching { java.time.LocalDate.parse(match.value) }.getOrNull()
        }

    /** 距今超过 [STALE_THRESHOLD_DAYS] 天的条目返回提示文本，供模型自行权衡可信度。 */
    private fun staleNote(line: String, today: java.time.LocalDate): String? {
        val date = lineDate(line) ?: return null
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        if (days <= STALE_THRESHOLD_DAYS) return null
        return "记录于 $date（${days} 天前），可能已过期"
    }

    private fun snapshotOf(
        content: String,
        bytes: ByteArray = content.toByteArray(Charsets.UTF_8),
    ): AgentMemorySnapshot = AgentMemorySnapshot(
        content = content,
        revision = sha256(bytes),
        byteSize = bytes.size,
        lineCount = content.memoryLines().size,
    )

    private fun String.memoryLines(): List<String> =
        if (isEmpty()) emptyList() else split('\n')

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }

    companion object {
        const val MAX_FILE_BYTES = 1024 * 1024
        const val DEFAULT_READ_CHARS = 12_000
        const val MAX_READ_CHARS = 32_000
        const val MIN_READ_CHARS = 1
        const val MAX_WRITE_CONTENT_CHARS = 3_500
        private const val DIRECTORY_NAME = "memory"
        private const val FILE_NAME = "MEMORY.md"
        private const val DEFAULT_START_LINE = 1
        private const val SEARCH_CONTEXT_LINES = 1
        // 检索打分权重：整串命中优先，其次命中词数与重复出现，结构化条目轻微加权。
        private const val SCORE_EXACT_PHRASE = 100
        private const val SCORE_TOKEN_HIT = 25
        private const val SCORE_REPEAT_BONUS = 3
        private const val SCORE_MAX_OCCURRENCES = 3
        private const val SCORE_ALL_TOKENS = 20
        private const val SCORE_STRUCTURED_LINE = 10
        /** 行内日期距今超过这个天数就标「可能已过期」。 */
        private const val STALE_THRESHOLD_DAYS = 30L
        private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        /** 渲染一行时 "行号: " 前缀与换行的额外开销，用于预算估算。 */
        private const val LINE_RENDER_OVERHEAD = 8
    }
}

internal object AgentMemoryRepository {
    @Volatile
    private lateinit var store: AgentMemoryStore

    fun init(context: Context) {
        if (!::store.isInitialized) {
            store = AgentMemoryStore(context.applicationContext.filesDir)
        }
    }

    fun snapshot(): AgentMemorySnapshot {
        ensureInitialized()
        return store.snapshot()
    }

    fun read(
        query: String? = null,
        startLine: Int = 1,
        maxChars: Int = AgentMemoryStore.DEFAULT_READ_CHARS,
    ): AgentMemoryReadResult {
        ensureInitialized()
        return store.read(query, startLine, maxChars)
    }

    fun mutate(mutation: AgentMemoryMutation): AgentMemoryWriteResult {
        ensureInitialized()
        return store.mutate(mutation)
    }

    fun replaceAll(content: String): AgentMemorySnapshot {
        ensureInitialized()
        return store.replaceAll(content)
    }

    fun enabledFlow(): Flow<Boolean> = SettingsDataStore.memoryEnabledFlow()

    suspend fun isEnabled(): Boolean = SettingsDataStore.settings().memoryEnabled

    suspend fun setEnabled(enabled: Boolean) = SettingsDataStore.setMemoryEnabled(enabled)

    private fun ensureInitialized() {
        check(::store.isInitialized) {
            "AgentMemoryRepository.init(context) must be called in Application.onCreate()"
        }
    }
}
