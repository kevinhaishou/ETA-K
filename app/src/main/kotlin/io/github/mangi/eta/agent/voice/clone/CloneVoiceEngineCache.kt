package io.github.mangi.eta.agent.voice.clone

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * zipvoice 模型实例的复用层。
 *
 * 实测 decoder（约 120MB）+ vocoder（约 54MB）每次加载要 7 秒左右，
 * 而试听时用户会反复点「合成并播放」，逐次 open/close 等于每次白等 7 秒
 * （短句约 0.7 秒音频总耗时 12 秒，其中约 7 秒是加载）。
 *
 * 这里在内存里保留一份实例，空闲 30 秒自动释放：既避免反复加载，
 * 又不会让 200MB 量级的模型长期占用内存被 LMK 盯上。
 */
internal object CloneVoiceEngineCache {
    private const val IDLE_RELEASE_MS = 30_000L

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var engine: CloneVoiceEngine? = null
    private var engineKey: String? = null
    /** 正在合成中的借用计数：>0 时不允许后台空闲释放，否则会关掉正在用的实例。 */
    private var inUse = 0

    private val releaseRunnable = Runnable { releaseIdle() }

    fun acquire(context: Context, directory: File): CloneVoiceEngine {
        val key = directory.absolutePath + "@" + directory.lastModified()
        synchronized(lock) {
            val cached = engine
            if (cached != null && engineKey == key) {
                inUse++
                cancelRelease()
                return cached
            }
            // 模型目录被换过或刚导入过，旧实例必须丢弃后重开。
            cached?.let { runCatching { it.close() } }
            val fresh = CloneVoiceEngine.open(context, directory)
            engine = fresh
            engineKey = key
            inUse++
            cancelRelease()
            return fresh
        }
    }

    /** 归还一次 [acquire] 借出的实例，随后开始空闲计时。 */
    fun release() {
        synchronized(lock) {
            if (inUse > 0) inUse--
            scheduleRelease()
        }
    }

    /** 立即释放：模型被清除、换模型、或内存吃紧时调用。 */
    fun closeNow() {
        synchronized(lock) {
            cancelRelease()
            runCatching { engine?.close() }
            engine = null
            engineKey = null
            inUse = 0
        }
    }

    private fun scheduleRelease() {
        cancelRelease()
        if (engine != null) handler.postDelayed(releaseRunnable, IDLE_RELEASE_MS)
    }

    private fun cancelRelease() {
        handler.removeCallbacks(releaseRunnable)
    }

    private fun releaseIdle() {
        synchronized(lock) {
            if (inUse > 0) {
                // 还在合成，推到下一个空闲窗口再试，绝不关正在用的实例。
                handler.postDelayed(releaseRunnable, IDLE_RELEASE_MS)
                return
            }
            runCatching { engine?.close() }
            engine = null
            engineKey = null
        }
    }
}
