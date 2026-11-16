package io.github.mangi.eta.agent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 系统离线 TTS：直接调用本机语音引擎播报，不经过云端合成，也不上传文本。
 *
 * 语言用 [Locale] 对象传入——Oplus 引擎按 BCP-47 字符串匹配时会挑不到中文音色。
 *
 * 系统引擎没有 pause：暂停只能停掉当前片段，因此播报以片段为单位循环，
 * [speak] 返回片段是否读完，由调用方决定前进还是原地重播。
 */
internal class SystemTtsPlayer(private val context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: CompletableDeferred<Unit>? = null
    private var interrupted = false

    /**
     * 播报到片段读完才返回；返回 false 表示被 [pause] 打断，该片段需要重播。
     *
     * 必须等读完：调用方在协程收尾时会把播放停掉，提前返回会让刚排队的语音立刻被 stop，
     * 表现为“界面看着在播、实际没声音”。
     */
    suspend fun speak(text: String): Boolean {
        val tts = ensureEngine()
            ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "本机没有可用的语音引擎")
        val language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "本机语音引擎缺少中文离线音色")
        }
        interrupted = false
        val done = CompletableDeferred<Unit>()
        pending = done
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                done.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                done.completeExceptionally(
                    SpeechFailure(SpeechErrorCode.PROTOCOL, "系统语音播报失败"),
                )
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                done.completeExceptionally(
                    SpeechFailure(SpeechErrorCode.PROTOCOL, "系统语音播报失败（$errorCode）"),
                )
            }
        })
        val accepted = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (accepted != TextToSpeech.SUCCESS) {
            pending = null
            throw SpeechFailure(SpeechErrorCode.AUDIO, "系统语音引擎拒绝了播报请求（$accepted）")
        }
        try {
            done.await()
            return !interrupted
        } finally {
            pending = null
        }
    }

    /** 停掉当前片段并让 [speak] 正常返回 false；片段之间没有正在播的内容时什么都不做。 */
    fun pause() {
        val active = pending ?: return
        interrupted = true
        active.complete(Unit)
        runCatching { engine?.stop() }
    }

    fun stop() {
        interrupted = true
        pending?.cancel()
        pending = null
        runCatching { engine?.stop() }
    }

    fun release() {
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    private suspend fun ensureEngine(): TextToSpeech? {
        engine?.let { if (ready) return it }
        return suspendCancellableCoroutine { continuation ->
            val created = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (continuation.isActive) continuation.resume(if (ready) engine else null)
            }
            engine = created
        }
    }

    private companion object {
        const val UTTERANCE_ID = "eta-system-tts"
    }
}