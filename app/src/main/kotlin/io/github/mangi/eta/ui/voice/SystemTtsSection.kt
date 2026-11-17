package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.voice.SystemTtsInfo
import io.github.mangi.eta.agent.voice.SystemTtsInspector
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 系统离线朗读的引擎信息与诊断。
 *
 * 只读展示本机引擎、默认音色、语言与可用中文音色；「重新检测」重新初始化一次引擎取最新状态。
 * 音色与语速由系统设置决定，这里只给入口，不在应用内另存一份配置。
 */
@Composable
internal fun SystemTtsSection() {
    val context = LocalContext.current
    val inspector = remember { SystemTtsInspector(context) }
    var info by remember { mutableStateOf(SystemTtsInfo()) }
    var revision by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    val secondary = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    val accent = MiuixTheme.colorScheme.primary

    LaunchedEffect(revision) {
        checking = true
        inspector.inspect { result ->
            info = result
            checking = false
        }
    }
    DisposableEffect(inspector) { onDispose { inspector.release() } }

    SpeechGroupCard(title = "系统离线朗读") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            TtsInfoRow("引擎", info.engine.ifBlank { if (checking) "检测中…" else "未知" }, secondary)
            TtsInfoRow("默认音色", info.defaultVoice.ifBlank { "—" }, secondary)
            TtsInfoRow("语言", info.language.ifBlank { "—" }, secondary)
            TtsInfoRow("可用中文音色", info.voices.size.toString() + " 个", secondary)
            info.error?.let { error ->
                Text(
                    text = error,
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFFC0392B),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            TtsAction("打开系统文字转语音设置") { SystemTtsInspector.openSettings(context) }
            Spacer(Modifier.height(8.dp))
            TtsAction(if (checking) "检测中…" else "重新检测", enabled = !checking) { revision++ }
            Text(
                text = "音色、语速与试听由系统引擎负责；这里只读展示，不改动系统设置。",
                style = MiuixTheme.textStyles.body2,
                color = secondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    if (info.voices.isNotEmpty()) {
        SpeechGroupCard(title = "可用音色") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                info.voices.forEach { voice ->
                    Text(
                        text = voice,
                        style = MiuixTheme.textStyles.body2,
                        color = accent,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsInfoRow(label: String, value: String, secondary: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = secondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
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