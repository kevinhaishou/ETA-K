package io.github.mangi.eta.ui.voice

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.voice.offline.OfflineAsrCatalog
import io.github.mangi.eta.agent.voice.offline.OfflineAsrModel
import io.github.mangi.eta.agent.voice.offline.OfflineAsrStore
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 本机离线的模型选择、校验状态与导入。
 *
 * 版式与官方离线页一致：正在使用 → 模型概览（模型下拉、体积/内存/引擎、就绪状态、三个动作）
 * → 可展开的说明分组。模型不进 APK，用户自己下载后在这里导入。
 */
@Composable
internal fun OfflineAsrSection(store: SpeechSettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val models = OfflineAsrCatalog.models
    val selectedIndex = models.indexOfFirst { it.id == store.settings.offlineModelId }
        .takeIf { it >= 0 } ?: models.indexOfFirst { it.id == "sense-voice-q8" }.coerceAtLeast(0)
    val selected: OfflineAsrModel = models[selectedIndex]
    @Suppress("UNUSED_EXPRESSION")
    revision
    val ready = OfflineAsrStore.isReady(context, selected.id)
    val secondary = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    val accent = MiuixTheme.colorScheme.primary

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val count = OfflineAsrStore.import(context, selected.id, uris)
                revision++
                "已导入 $count 个文件"
            } catch (error: Exception) {
                error.message ?: "导入失败"
            }
        }
    }

    Column {
        SpeechGroupCard(title = "正在使用") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "下次录音",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = selected.displayName + " · 已选 · " + if (ready) "就绪" else "未下载",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
        }

        SpeechGroupCard(title = "模型概览") {
            CouiDropdownPreference(
                title = "模型",
                items = models.map { it.displayName },
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    models.getOrNull(index)?.let { model ->
                        store.edit(store.settings.copy(offlineModelId = model.id))
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = selected.description,
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                OfflineInfoRow("下载大小", selected.sizeLabel, secondary)
                OfflineInfoRow("RAM 参考（含测试）", "约" + selected.ramLabel, secondary)
                OfflineInfoRow("引擎 / 量化", selected.engineLabel, secondary)

                Spacer(Modifier.height(10.dp))
                StatusBox(
                    ready = ready,
                    readyText = if (OfflineAsrCatalog.isRunnable(selected)) {
                        "已通过完整校验，可离线使用。"
                    } else {
                        "文件已就位，这个量化格式还没接入推理。"
                    },
                )
                Spacer(Modifier.height(12.dp))

                OfflineAction("确认使用并保存") {
                    store.edit(store.settings.copy(offlineModelId = selected.id))
                    store.save { }
                    message = "已保存为当前离线模型"
                }
                Spacer(Modifier.height(8.dp))
                OfflineAction("下载模型（联网）") {
                    message = "手机上网速有限，建议在电脑上下载后导入"
                }
                Spacer(Modifier.height(8.dp))
                OfflineAction("导入 GGUF / ZIP") {
                    picker.launch(arrayOf("*/*"))
                }
                message?.let { text ->
                    Text(
                        text = text,
                        style = MiuixTheme.textStyles.body2,
                        color = accent,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        SpeechGroupCard {
            OfflineExpandable(
                title = "更多参数与测试数据",
                expanded = expanded == "more",
                onToggle = { expanded = if (expanded == "more") null else "more" },
                secondary = secondary,
            ) {
                Text(
                    text = "识别整段录音后一次性解码，不做流式；16 kHz 单声道。\n" +
                        "内存占用随模型与录音长度变化，表中数值为 60 秒音频的参考值。",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
            OfflineExpandable(
                title = "导入说明",
                expanded = expanded == "import",
                onToggle = { expanded = if (expanded == "import") null else "import" },
                secondary = secondary,
            ) {
                Text(
                    text = "SenseVoice INT8：需要 model.int8.onnx 与 tokens.txt 两个文件，一起选中即可。\n" +
                        "GGUF：选中 .gguf 权重；若与 tokens 分离，也一并选上。\n" +
                        "文件会复制到应用私有目录，原文件可以删除。",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
            OfflineExpandable(
                title = "许可与来源",
                expanded = expanded == "license",
                onToggle = { expanded = if (expanded == "license") null else "license" },
                secondary = secondary,
            ) {
                Text(
                    text = "运行库：sherpa-onnx（Apache-2.0）、ONNX Runtime（MIT）。\n" +
                        "模型：SenseVoice（FunAudioLLM，权重遵循其发布许可）、Qwen3-ASR（通义千问系列许可）。\n" +
                        "可下载不等于可再分发，二次分发请自行核对各自许可条款。",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
        }
    }
}

@Composable
private fun OfflineInfoRow(label: String, value: String, secondary: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
private fun StatusBox(ready: Boolean, readyText: String) {
    val background = if (ready) Color(0xFF2E9E4F).copy(alpha = 0.12f) else Color(0xFF8A8A8A).copy(alpha = 0.12f)
    val foreground = if (ready) Color(0xFF2E9E4F) else Color(0xFF6B6B6B)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (ready) "就绪" else "未下载",
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = foreground,
        )
        Text(
            text = if (ready) readyText else "还没有导入这个模型的权重文件。",
            style = MiuixTheme.textStyles.body2,
            color = foreground,
        )
    }
}

@Composable
private fun OfflineAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body1,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun OfflineExpandable(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    secondary: Color,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "⌃" else "⌄",
                style = MiuixTheme.textStyles.body1,
                color = secondary,
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                content()
            }
        }
    }
}