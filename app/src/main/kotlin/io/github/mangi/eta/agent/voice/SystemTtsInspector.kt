package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 系统 TTS 的元数据查询与音色选择。
 *
 * 只读元数据：不发声、不合成、不下载语言包、不读凭据。
 * 离线与否严格按引擎声明判断（`isNetworkConnectionRequired` 与 features 标签），不做流量审计。
 */
internal data class SystemTtsDiagnostics(val text: String)

internal object SystemTtsInspector {
    /** 语言标记规范化：三字母语言码归一（zho/cmn/chi → zh），三字母国家转两字母。 */
    fun systemTtsLocale(tag: String): Locale {
        val parts = tag.trim().replace('_', '-').split('-').toMutableList()
        if (parts.isEmpty() || parts[0].isBlank()) return Locale.ROOT
        parts[0] = canonicalLanguage(parts[0])
        for (i in 1 until parts.size) {
            if (parts[i].length == 3 && parts[i].all(Char::isLetter)) {
                parts[i] = canonicalCountry(parts[i])
            }
        }
        return Locale.forLanguageTag(parts.joinToString("-"))
    }

    private fun canonicalLanguage(language: String): String = when (language.lowercase(Locale.ROOT)) {
        "zho", "cmn", "chi" -> "zh"
        "eng" -> "en"
        else -> language.lowercase(Locale.ROOT)
    }

    private fun canonicalCountry(country: String): String {
        val upper = country.uppercase(Locale.ROOT)
        if (upper.length != 3) return upper
        return Locale.getISOCountries().firstOrNull {
            runCatching { Locale("", it).isO3Country == upper }.getOrDefault(false)
        } ?: upper
    }

    /**
     * 音色 locale 归一化：只做与引擎无关的标准化（三字母语言/国家码折算）。
     *
     * 本机引擎是荣耀 com.hihonor.voiceengine，不做任何厂商特判；实际数据以诊断输出为准，
     * 确认存在畸形标记后再针对该引擎加别名，不预先猜。
     */
    fun normalizedSystemVoiceLocale(locale: Locale, @Suppress("UNUSED_PARAMETER") engine: String?): Locale {
        return systemTtsLocale(
            if (locale.country.length == 3) "${locale.language}-${locale.country}" else locale.toLanguageTag(),
        )
    }

    /** 引擎声明里既不需要联网、也没被标记为未安装，才算可用的离线音色。 */
    fun isInstalledOfflineSystemVoice(voice: Voice): Boolean {
        val features = voice.features.orEmpty()
        return !voice.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS !in features &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in features
    }

    /** 精确 locale 优先，其次默认音色，最后按名称排序，保证同一份设置挑到同一个音色。 */
    fun chooseOfflineSystemVoice(
        voices: Set<Voice>,
        locale: Locale,
        defaultName: String?,
        engine: String? = null,
    ): Voice? {
        val target = normalizedSystemVoiceLocale(locale, null)
        return voices.asSequence().filter {
            val candidate = normalizedSystemVoiceLocale(it.locale, engine)
            isInstalledOfflineSystemVoice(it) && target.language.isNotBlank() &&
                candidate.language == target.language &&
                (target.script.isEmpty() || candidate.script.isEmpty() || target.script == candidate.script)
        }.sortedWith(
            compareByDescending<Voice> { normalizedSystemVoiceLocale(it.locale, engine) == target }
                .thenByDescending { it.name == defaultName }
                .thenBy { it.name },
        ).firstOrNull()
    }

    fun offlineSystemVoiceFailure(voices: Set<Voice>, locale: Locale, engine: String?): String = when {
        voices.isEmpty() ->
            "引擎未提供音色元数据，无法确认离线；可在系统朗读设置中显式允许使用系统默认（联网由引擎决定）"
        voices.any {
            normalizedSystemVoiceLocale(it.locale, engine).language == locale.language &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty()
        } -> "引擎将该语言音色标记为未安装，不能确认可离线使用；请在系统文字转语音设置中检查语音数据"
        else -> "引擎未提供该语言可确认已安装的离线音色；不会自动下载或切换联网音色，请检查系统文字转语音设置"
    }

    suspend fun inspect(
        context: Context,
        engineName: String,
        language: String,
    ): SystemTtsDiagnostics = withContext(Dispatchers.IO) {
        var tts: TextToSpeech? = null
        try {
            withTimeout(15_000) {
                val ready = CompletableDeferred<Int>()
                val instance = withContext(Dispatchers.Main.immediate) {
                    TextToSpeech(
                        context.applicationContext,
                        { ready.complete(it) },
                        engineName.trim().ifEmpty { null },
                    ).also { tts = it }
                }
                check(ready.await() == TextToSpeech.SUCCESS) { "系统语音引擎初始化失败" }
                val engine = engineName.trim().ifEmpty { instance.defaultEngine }
                val voices = instance.voices.orEmpty()
                val default = instance.defaultVoice
                val locale = systemTtsLocale(language.ifBlank { "zh-CN" })
                val selected = chooseOfflineSystemVoice(voices, locale, default?.name, engine)
                SystemTtsDiagnostics(
                    buildString {
                        appendLine("引擎：$engine")
                        appendLine("默认音色：${default?.name ?: "未提供"}")
                        appendLine("默认语言：${instance.language?.toLanguageTag() ?: "未提供"}")
                        appendLine("默认音色 locale：${default?.locale?.toLanguageTag() ?: "未提供"}")
                        appendLine("目标语言：${locale.toLanguageTag()}；可用性：${instance.isLanguageAvailable(locale)}")
                        val installed = voices.count {
                            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                        }
                        val offline = voices.count { isInstalledOfflineSystemVoice(it) }
                        appendLine("音色总数：${voices.size}；已安装标记：$installed；离线可选：$offline")
                        if (selected != null) {
                            appendLine(
                                "离线匹配：${selected.name}" +
                                    "（${normalizedSystemVoiceLocale(selected.locale, engine).toLanguageTag()}）",
                            )
                        } else {
                            appendLine(offlineSystemVoiceFailure(voices, locale, engine))
                        }
                        voices.sortedBy { it.name }.forEach {
                            appendLine(
                                "${it.name}：${it.locale.toLanguageTag()} → " +
                                    "${normalizedSystemVoiceLocale(it.locale, engine).toLanguageTag()}；" +
                                    "network=${it.isNetworkConnectionRequired}；features=${it.features}",
                            )
                        }
                        append("仅检测元数据，不发声、不录音、不下载。离线判断依据引擎声明，不是流量审计。")
                    },
                ).also { android.util.Log.i("EtaSystemTts", it.text.replace("\n", " | ")) }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { runCatching { tts?.shutdown() } }
        }
    }

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