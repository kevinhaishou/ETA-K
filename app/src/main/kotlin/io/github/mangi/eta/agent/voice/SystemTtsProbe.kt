package io.github.mangi.eta.agent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 直接调用本机 TTS 引擎的最小实现，不依赖播报控制器与前台服务。
 *
 * 用于「系统离线语音」以及试听自检：引擎初始化、语言支持、speak 返回值
 * 都会通过 [onResult] 回调成一句可读的中文状态，便于界面直接提示。
 */
internal object SystemTtsProbe {
    @Volatile
    private var engine: TextToSpeech? = null

    fun speak(
        context: Context,
        text: String,
        onResult: (String) -> Unit,
        onDone: () -> Unit = {},
    ) {
        val cached = engine
        if (cached != null) {
            startSpeaking(cached, text, onResult, onDone)
            return
        }
        var created: TextToSpeech? = null
        created = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                onResult("引擎初始化失败（$status）")
                return@TextToSpeech
            }
            engine = created
            val active = created
            if (active == null) {
                onResult("引擎创建失败")
                return@TextToSpeech
            }
            startSpeaking(active, text, onResult, onDone)
        }
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    private fun startSpeaking(
        tts: TextToSpeech,
        text: String,
        onResult: (String) -> Unit,
        onDone: () -> Unit,
    ) {
        val language = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (language == TextToSpeech.LANG_MISSING_DATA ||
            language == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            onResult("本机引擎缺少中文离线音色（$language）")
            return
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                onDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onResult("播报失败")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onResult("播报失败（$errorCode）")
            }
        })
        val result = tts.speak(text.take(300), TextToSpeech.QUEUE_FLUSH, null, "eta-probe")
        onResult(if (result == TextToSpeech.SUCCESS) "已调用本机引擎" else "speak 被拒绝（$result）")
    }
}