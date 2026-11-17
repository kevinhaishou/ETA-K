package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.voice.SystemTtsInspector
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val RateOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val RateLabels = RateOptions.map { String.format("%.2fx", it) }

/**
 * 系统离线朗读：仅离线音色开关、引擎包名、语言、语速与音调，以及引擎诊断。
 *
 * 这些设置只作用在本机 TTS 引擎调用上，不改系统设置；诊断只读元数据，
 * 不发声、不录音、不下载语言包。
 */
@Composable
internal fun SystemTtsSection(store: SpeechSettingsStore) {
    val context = LocalContext.current
    val settings = store.settings
    var diagnostics by remember { mutableStateOf("正在检测引擎…") }
    var revision by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(true) }
    val secondary = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f)

    LaunchedEffect(revision, settings.systemTtsEngine, settings.systemTtsLanguage) {
        checking = true
        diagnostics = runCatching {
            SystemTtsInspector.inspect(
                context,
                settings.systemTtsEngine,
                settings.systemTtsLanguage,
            ).text
        }.getOrElse { "检测失败：" + (it.message ?: it.javaClass.simpleName) }
        checking = false
    }

    SpeechGroupCard(title = "声音") {
        CouiSwitchPreference(
            title = "仅离线音色",
            checked = settings.systemTtsOfflineOnly,
            onCheckedChange = { store.edit(settings.copy(systemTtsOfflineOnly = it)) },
            summary = "开启：只使用引擎声明已安装且无需联网的音色。关闭：允许使用系统默认（联网由引擎决定）；" +
                "不会在播放失败后自动切换。",
        )
        SpeechFieldColumn {
            SpeechField(
                label = "引擎包名（留空使用系统默认）",
                value = settings.systemTtsEngine,
                onChange = { store.edit(store.settings.copy(systemTtsEngine = it.trim())) },
            )
            SpeechField(
                label = "离线音色语言（如 zh-CN；留空使用语音语言）",
                value = settings.systemTtsLanguage,
                onChange = { store.edit(store.settings.copy(systemTtsLanguage = it.trim())) },
            )
            CouiDropdownPreference(
                title = "语速",
                items = RateLabels,
                selectedIndex = nearestIndex(settings.systemTtsRate),
                onSelectedIndexChange = { index ->
                    RateOptions.getOrNull(index)?.let {
                        store.edit(store.settings.copy(systemTtsRate = it))
                    }
                },
            )
            CouiDropdownPreference(
                title = "音调",
                items = RateLabels,
                selectedIndex = nearestIndex(settings.systemTtsPitch),
                onSelectedIndexChange = { index ->
                    RateOptions.getOrNull(index)?.let {
                        store.edit(store.settings.copy(systemTtsPitch = it))
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            TtsAction("打开系统文字转语音设置") { SystemTtsInspector.openSettings(context) }
        }
    }

    SpeechGroupCard(title = "系统 TTS 诊断") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = diagnostics,
                style = MiuixTheme.textStyles.body2,
                color = secondary,
            )
            Spacer(Modifier.height(10.dp))
            TtsAction(if (checking) "检测中…" else "重新检测", enabled = !checking) { revision++ }
        }
    }
}

private fun nearestIndex(value: Float): Int {
    var best = 0
    var delta = Float.MAX_VALUE
    RateOptions.forEachIndexed { index, option ->
        val d = kotlin.math.abs(option - value)
        if (d < delta) {
            delta = d
            best = index
        }
    }
    return best
}

@Composable
private fun TtsAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body1,
        color = if (enabled) {
            MiuixTheme.colorScheme.onSurface
        } else {
            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}