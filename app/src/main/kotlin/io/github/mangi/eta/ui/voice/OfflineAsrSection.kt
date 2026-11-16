package io.github.mangi.eta.ui.voice

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.voice.offline.OfflineAsrCatalog
import io.github.mangi.eta.agent.voice.offline.OfflineAsrStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 离线模型选择与导入。
 *
 * 模型不进 APK：用户自己把权重放进手机后在这里导入，目录是应用私有空间。
 * 选中哪一项由设置里的 offlineModelId 决定，识别时按它去找文件。
 */
@Composable
internal fun OfflineAsrSection(store: SpeechSettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val selectedId = store.settings.offlineModelId

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val target = selectedId.ifBlank { OfflineAsrCatalog.models.first().id }
        scope.launch {
            message = try {
                val count = OfflineAsrStore.import(context, target, uris)
                revision++
                "已导入 $count 个文件"
            } catch (error: Exception) {
                error.message ?: "导入失败"
            }
        }
    }

    val secondary = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    SpeechGroupCard(title = "离线模型") {
        // revision 参与读取，导入完成后强制重算就绪状态。
        OfflineAsrCatalog.models.forEach { model ->
            @Suppress("UNUSED_EXPRESSION")
            revision
            val ready = OfflineAsrStore.isReady(context, model.id)
            val active = model.id == selectedId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { store.edit(store.settings.copy(offlineModelId = model.id)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = (if (active) "● " else "○ ") + model.displayName + "   " + model.sizeLabel,
                    style = MiuixTheme.textStyles.body2,
                    color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "RAM " + model.ramLabel + " · " + if (ready) "就绪" else "未下载",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
        }
        Text(
            text = "模型只在本机加载，音频不会上传。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MiuixTheme.textStyles.body2,
            color = secondary,
        )
    }

    SpeechGroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { picker.launch(arrayOf("*/*")) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "导入模型文件",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = "选 SenseVoice INT8 的 model.int8.onnx 与 tokens.txt；GGUF 选 .gguf 文件",
                style = MiuixTheme.textStyles.body2,
                color = secondary,
            )
        }
        message?.let { text ->
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}