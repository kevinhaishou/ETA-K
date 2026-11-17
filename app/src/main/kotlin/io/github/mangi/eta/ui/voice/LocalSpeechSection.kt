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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.voice.local.LocalSpeechBackend
import io.github.mangi.eta.agent.voice.local.LocalSpeechEngine
import io.github.mangi.eta.agent.voice.local.LocalSpeechModelDescriptor
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 本机离线：模型选择、校验状态、下载与导入。
 *
 * 清单以 assets/local_speech/catalog.json 为准，内含 GGUF（Qwen3-ASR、SenseVoice）、
 * SenseVoice INT8 ONNX 与两档 Vosk。识别在 :local_speech 独立进程里跑，音频不出本机。
 */
@Composable
internal fun LocalSpeechSection(store: SpeechSettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<LocalSpeechModelDescriptor>>(emptyList()) }
    var selectedId by remember { mutableStateOf("") }
    var installed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val secondary = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    val accent = MiuixTheme.colorScheme.primary

    LaunchedEffect(revision) {
        models = runCatching { LocalSpeechEngine.availableModels(context) }.getOrDefault(emptyList())
        selectedId = runCatching { LocalSpeechEngine.selectedModelId(context) }.getOrDefault("")
        installed = runCatching {
            selectedId.isNotEmpty() && LocalSpeechEngine.inspect(context, selectedId).installed
        }.getOrDefault(false)
    }

    val selected = models.firstOrNull { it.id == selectedId } ?: models.firstOrNull()
    val selectedIndex = selected?.let { model -> models.indexOfFirst { it.id == model.id } } ?: 0

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val model = selected ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = try {
                LocalSpeechEngine.importModel(context, model.id) {
                    context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
                }
                revision++
                "已导入并校验"
            } catch (error: Exception) {
                error.message ?: "导入失败"
            } finally {
                busy = false
            }
        }
    }

    if (models.isEmpty()) {
        SpeechGroupCard {
            Text(
                text = "离线模型清单加载失败",
                modifier = Modifier.padding(16.dp),
                style = MiuixTheme.textStyles.body2,
                color = secondary,
            )
        }
        return
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
                    text = (selected?.displayName ?: "未选择") + " · " + if (installed) "就绪" else "未下载",
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
                        LocalSpeechEngine.setSelectedModelId(context, model.id)
                        selectedId = model.id
                        revision++
                    }
                },
            )
            selected?.let { model ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = model.description,
                        style = MiuixTheme.textStyles.body2,
                        color = secondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    LocalInfoRow("下载大小", formatBytes(model.sizeBytes), secondary)
                    LocalInfoRow("架构 / 量化", model.architecture + " / " + model.quantization, secondary)
                    LocalInfoRow("引擎", backendLabel(model.engine), secondary)
                    LocalInfoRow("许可", model.licenseName, secondary)

                    Spacer(Modifier.height(10.dp))
                    LocalStatusBox(
                        ready = installed,
                        readyText = "已通过完整校验，可离线使用。",
                        missingText = model.availabilityReason ?: "还没有下载这个模型。",
                    )
                    Spacer(Modifier.height(12.dp))

                    LocalAction(if (busy) "处理中…" else "确认使用并保存", enabled = !busy) {
                        LocalSpeechEngine.setSelectedModelId(context, model.id)
                        message = "已保存为当前离线模型"
                    }
                    Spacer(Modifier.height(8.dp))
                    LocalAction("下载模型（联网）", enabled = !busy) {
                        scope.launch {
                            busy = true
                            message = try {
                                LocalSpeechEngine.download(context, model.id) { }
                                revision++
                                "下载完成"
                            } catch (error: Exception) {
                                error.message ?: "下载失败"
                            } finally {
                                busy = false
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LocalAction("导入本地文件", enabled = !busy) {
                        importer.launch(arrayOf("*/*"))
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
        }

        SpeechGroupCard {
            LocalExpandable(
                title = "更多参数与测试数据",
                expanded = expanded == "more",
                onToggle = { expanded = if (expanded == "more") null else "more" },
                secondary = secondary,
            ) {
                Text(
                    text = "后端按模型格式自动选择：\n" +
                        "GGUF → transcribe.cpp（CPU / ggml）\n" +
                        "ONNX → sherpa-onnx（SenseVoice INT8）\n" +
                        "Vosk → vosk-android\n" +
                        "模型在独立进程内按需加载，空闲或内存压力下释放。",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
            LocalExpandable(
                title = "导入说明",
                expanded = expanded == "import",
                onToggle = { expanded = if (expanded == "import") null else "import" },
                secondary = secondary,
            ) {
                Text(
                    text = "按所选模型接收对应格式：\n" +
                        "· GGUF：单个 .gguf 权重\n" +
                        "· SenseVoice INT8：model.int8.onnx + tokens.txt\n" +
                        "· Vosk：模型目录打包的 zip\n" +
                        "导入后会校验文件大小与 SHA-256，校验不通过不会启用。",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
            LocalExpandable(
                title = "许可与来源",
                expanded = expanded == "license",
                onToggle = { expanded = if (expanded == "license") null else "license" },
                secondary = secondary,
            ) {
                Text(
                    text = "运行库：transcribe.cpp / ggml（MIT）、sherpa-onnx（Apache-2.0）、" +
                        "ONNX Runtime（MIT）、Vosk（Apache-2.0）。\n" +
                        "模型：Qwen3-ASR（通义千问系列许可）、SenseVoice（FunAudioLLM 发布许可）、" +
                        "Vosk 中文模型（Apache-2.0）。\n" +
                        "可下载不等于可再分发，二次分发请自行核对各自许可条款。",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
            }
        }
    }
}

private fun backendLabel(backend: LocalSpeechBackend): String = when (backend) {
    LocalSpeechBackend.GGUF -> "transcribe.cpp / ggml"
    LocalSpeechBackend.SENSEVOICE_ONNX -> "sherpa-onnx"
    LocalSpeechBackend.VOSK -> "vosk-android"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) String.format("%.2f GB", mb / 1024) else String.format("%.1f MB", mb)
}

@Composable
private fun LocalInfoRow(label: String, value: String, secondary: Color) {
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
private fun LocalStatusBox(ready: Boolean, readyText: String, missingText: String) {
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
            text = if (ready) readyText else missingText,
            style = MiuixTheme.textStyles.body2,
            color = foreground,
        )
    }
}

@Composable
private fun LocalAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
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

@Composable
private fun LocalExpandable(
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