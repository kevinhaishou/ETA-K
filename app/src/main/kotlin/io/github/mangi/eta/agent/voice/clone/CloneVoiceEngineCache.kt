package io.github.mangi.eta.agent.voice.clone

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * zipvoice 模型实例的复用层。
 *
 * 实测 decoder（约 120MB）+ vocoder（约 54MB）每次加载要 7 秒左右，
 * 而试听时用户会反复点「合成并播放」，逐次 open/close 等于每次白等 7 秒
 * （短句约 0.7 秒音频总耗时 12 秒，其中约 7 秒是加载）。
 *
 * 这里在内存里保留一份实例，空闲 30 秒自动释放：既避免反复加载，
 * 又不会让 200MB 量级的模型长期占用内存被 LMK 盯上。
 *
 * 两个线程纪律：
 * 1. 加锁只做引用计数与指针读写，**加载与释放都放在锁外**。
 *    加载要几秒，如果抱着锁做，主线程上的空闲释放就会阻塞到 ANR。
 * 2. 空闲释放走独立调度线程，不在主线程释放 180MB 的 native 内存。
 */
internal object CloneVoiceEngineCache {
    private const val IDLE_RELEASE_MS = 30_000L

    private val lock = Any()
    /** 串行化模型加载：两个线程同时未命中会各 open 一份 180MB 模型，手机上等于自杀。 */
    private val loadLock = Any()
    private var engine: CloneVoiceEngine? = null
    private var engineKey: String? = null
    /** 借用计数：>0 时不允许空闲释放，否则会关掉正在用的实例。 */
    private var inUse = 0

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "clone-voice-idle").apply { isDaemon = true }
    }
    private var releaseTask: ScheduledFuture<*>? = null

    fun acquire(context: Context, directory: File): CloneVoiceEngine {
        val key = directory.absolutePath + "@" + directory.lastModified()
        synchronized(lock) {
            val cached = engine
            if (cached != null && engineKey == key) {
                inUse++
                cancelReleaseLocked()
                return cached
            }
        }
        // 加载在 lock 之外做，但由 loadLock 串行：
        // 后来者在这里等前一个装完，然后直接命中缓存，不会同时存在两份 180MB 模型。
        synchronized(loadLock) {
            synchronized(lock) {
                val cached = engine
                if (cached != null && engineKey == key) {
                    inUse++
                    cancelReleaseLocked()
                    return cached
                }
            }
            val fresh = CloneVoiceEngine.open(context, directory)
            synchronized(lock) {
                val existing = engine
                if (existing != null && engineKey == key) {
                    // 极端情况下被别处装好了，保留先到的那个。
                    runCatching { fresh.close() }
                    inUse++
                    cancelReleaseLocked()
                    return existing
                }
                // 模型目录被换过或刚重新导入过，旧实例必须丢弃。
                runCatching { existing?.close() }
                engine = fresh
                engineKey = key
                inUse++
                cancelReleaseLocked()
                return fresh
            }
        }
    }

    /** 归还一次 [acquire] 借出的实例，随后开始空闲计时。 */
    fun release() {
        synchronized(lock) {
            if (inUse > 0) inUse--
            if (inUse == 0 && engine != null) scheduleReleaseLocked()
        }
    }

    /** 立即释放：换模型或内存吃紧时调用。合成进行中会留给空闲释放，避免打断 native。 */
    fun closeNow() {
        // 用 var 是必须的：Kotlin 不允许在 lambda 里初始化捕获的 val。
        var victim: CloneVoiceEngine? = null
        synchronized(lock) {
            val current = engine
            if (current != null && current.isBusy) {
                // 有合成在跑：只停掉计时，等合成结束后的下一次 release 再释放。
                cancelReleaseLocked()
                inUse = 0
                scheduleReleaseLocked()
                return
            }
            cancelReleaseLocked()
            victim = current
            engine = null
            engineKey = null
            inUse = 0
        }
        runCatching { victim?.close() }
    }

    private fun scheduleReleaseLocked() {
        cancelReleaseLocked()
        releaseTask = scheduler.schedule({ releaseIdle() }, IDLE_RELEASE_MS, TimeUnit.MILLISECONDS)
    }

    private fun cancelReleaseLocked() {
        releaseTask?.cancel(false)
        releaseTask = null
    }

    private fun releaseIdle() {
        var victim: CloneVoiceEngine? = null
        synchronized(lock) {
            releaseTask = null
            val current = engine
            if (inUse > 0 || current == null) {
                // 又有人借走了，推到下一个空闲窗口再试，绝不关正在用的实例。
                if (current != null) scheduleReleaseLocked()
                return
            }
            // 已取消的合成协程可能还卡在 native 里，这时 release 会 use-after-free。
            if (current.isBusy) {
                scheduleReleaseLocked()
                return
            }
            victim = current
            engine = null
            engineKey = null
        }
        runCatching { victim?.close() }
    }
}
