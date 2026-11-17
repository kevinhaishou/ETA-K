package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 本机 TTS 引擎的一次探测结果，供诊断面板展示。 */
internal data class SystemTtsInfo(
    val engine: String = "",
    val defaultVoice: String = "",
    val language: String = "",
    val voices: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * 查询系统 TTS 引擎：默认引擎包名、默认音色、语言，以及可用的中文音色列表。
 *
 * 只读探测，不参与播报；查询结束立刻释放引擎，避免与播报用的实例抢资源。
 */
internal class SystemTtsInspector(private val context: Context) {
    private var engine: TextToSpeech? = null

    fun inspect(onResult: (SystemTtsInfo) -> Unit) {
        runCatching { engine?.shutdown() }
        engine = null
        val created = TextToSpeech(context.applicationContext) { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                onResult(SystemTtsInfo(error = "引擎初始化失败（$status）"))
                return@TextToSpeech
            }
            val all = runCatching { tts.voices?.map { it.name }.orEmpty() }.getOrDefault(emptyList())
            val chinese = all.filter {
                it.contains("zh", ignoreCase = true) || it.contains("cmn", ignoreCase = true)
            }
            onResult(
                SystemTtsInfo(
                    engine = runCatching { tts.defaultEngine }.getOrNull().orEmpty(),
                    defaultVoice = runCatching { tts.defaultVoice?.name }.getOrNull().orEmpty(),
                    language = runCatching { tts.defaultVoice?.locale?.toLanguageTag() }
                        .getOrNull().orEmpty(),
                    voices = chinese.ifEmpty { all },
                ),
            )
        }
        engine = created
    }

    fun release() {
        runCatching { engine?.shutdown() }
        engine = null
    }

    companion object {
        /** 打开系统的文字转语音设置页，不同 ROM 的入口名不一致，逐个尝试。 */
        fun openSettings(context: Context) {
            val candidates = listOf(
                Intent("com.android.settings.TTS_SETTINGS"),
                Intent(android.provider.Settings.ACTION_SETTINGS),
            )
            for (intent in candidates) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
        }

        fun chineseLocale(): Locale = Locale.SIMPLIFIED_CHINESE
    }
}