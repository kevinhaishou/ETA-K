package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** 单个音色的元数据，仅用于诊断展示，不参与发声。 */
internal data class SystemTtsVoice(
    val name: String,
    val locale: String,
    val networkRequired: Boolean,
    /** Voice.getFeatures() 返回的是标签集合，不是位掩码。 */
    val features: String,
)

/**
 * 本机 TTS 引擎的一次探测结果。
 *
 * 只读元数据：查询过程不发声、不录音、不下载语言包；离线与否依据引擎自己的声明
 * （`isNetworkConnectionRequired`），不做流量审计。
 */
internal data class SystemTtsInfo(
    val engine: String = "",
    val defaultVoice: String = "",
    val defaultLanguage: String = "",
    val defaultLocale: String = "",
    val targetLanguage: String = "",
    val targetAvailability: Int = -1,
    val voiceCount: Int = 0,
    val installedCount: Int = 0,
    val offlineCount: Int = 0,
    val offlineMatch: String = "",
    val voices: List<SystemTtsVoice> = emptyList(),
    val error: String? = null,
)

internal class SystemTtsInspector(private val context: Context) {
    private var engine: TextToSpeech? = null

    fun inspect(
        engineName: String = "",
        languageTag: String = "zh-CN",
        onResult: (SystemTtsInfo) -> Unit,
    ) {
        runCatching { engine?.shutdown() }
        engine = null
        val app = context.applicationContext
        val listener = TextToSpeech.OnInitListener { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                onResult(SystemTtsInfo(error = "引擎初始化失败（$status）"))
                return@OnInitListener
            }
            val target = runCatching { Locale.forLanguageTag(languageTag.ifBlank { "zh-CN" }) }
                .getOrDefault(Locale.SIMPLIFIED_CHINESE)
            val voices = runCatching { tts.voices?.toList().orEmpty() }
                .getOrDefault(emptyList())
            val mapped = voices.map { voice: Voice ->
                SystemTtsVoice(
                    name = voice.name,
                    locale = voice.locale.toLanguageTag(),
                    networkRequired = voice.isNetworkConnectionRequired,
                    features = voice.features?.joinToString(",").orEmpty(),
                )
            }
            val offline = mapped.filter { !it.networkRequired }
            val matched = offline.firstOrNull { it.locale.equals(target.toLanguageTag(), true) }
                ?: offline.firstOrNull { it.locale.startsWith(target.language, true) }
            onResult(
                SystemTtsInfo(
                    engine = runCatching { tts.defaultEngine }.getOrNull().orEmpty(),
                    defaultVoice = runCatching { tts.defaultVoice?.name }.getOrNull().orEmpty(),
                    defaultLanguage = runCatching { tts.defaultVoice?.locale?.isO3Language }
                        .getOrNull().orEmpty(),
                    defaultLocale = runCatching { tts.defaultVoice?.locale?.language }
                        .getOrNull().orEmpty(),
                    targetLanguage = target.toLanguageTag(),
                    targetAvailability = runCatching { tts.isLanguageAvailable(target) }.getOrDefault(-1),
                    voiceCount = mapped.size,
                    installedCount = mapped.size,
                    offlineCount = offline.size,
                    offlineMatch = matched?.let { it.name + "（" + it.locale + "）" } ?: "无",
                    voices = mapped,
                ),
            )
        }
        engine = if (engineName.isBlank()) {
            TextToSpeech(app, listener)
        } else {
            runCatching { TextToSpeech(app, listener, engineName) }
                .getOrElse { TextToSpeech(app, listener) }
        }
    }

    fun release() {
        runCatching { engine?.shutdown() }
        engine = null
    }

    companion object {
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
    }
}