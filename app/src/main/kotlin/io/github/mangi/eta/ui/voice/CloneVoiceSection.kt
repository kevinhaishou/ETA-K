package io.github.mangi.eta.ui.voice

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.voice.clone.CloneVoiceEngine
import io.github.mangi.eta.agent.voice.clone.CloneVoicePlayback
import io.github.mangi.eta.agent.voice.clone.CloneVoiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 语音克隆（实验）：导入模型目录 + 导入参考音频，用参考音频的音色合成指定文本。
 *
 * 模型是 zipvoice zero-shot，权重不进 APK；参考音频支持任意常见格式（经系统解码到 16kHz）。
 * 合成在本机 CPU 上跑，长句会慢，这里先给最小闭环用于验收。
 */
@Composable
internal fun CloneVoiceSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var referenceSeconds by remember { mutableIntStateOf(0) }
    var referenceText by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("这是一段用你的音色合成的测试语音。") }
    var recording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<io.github.mangi.eta.agent.voice.SpeechRecorder?>(null) }

    @Suppress("UNUSED_EXPRESSION")
    revision
    val modelReady = CloneVoiceStore.isModelReady(context)
    val hasReference = CloneVoiceStore.hasReference(context)
    val secondary = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    val accent = MiuixTheme.colorScheme.primary

    fun report(message: String, isError: Boolean = false) {
        status = message
        statusIsError = isError
    }

    val pickModel = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val copied = CloneVoiceStore.importTree(context, uri)
                revision++
                if (copied == 0) {
                    report("这个目录里没有可导入的文件", isError = true)
                } else {
                    report("已导入 $copied 个文件")
                }
            } catch (error: Exception) {
                val reason = if (error is io.github.mangi.eta.agent.voice.SpeechFailure) {
                    error.userMessage
                } else {
                    error.message ?: error.javaClass.simpleName
                }
                android.util.Log.e("EtaVoiceClone", "import failed: $reason", error)
                report(reason, isError = true)
            } finally {
                busy = false
            }
        }
    }

    val pickReference = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val (_, seconds) = CloneVoiceStore.importReference(context, uri)
                referenceSeconds = seconds
                revision++
                report("参考音频已导入，约 $seconds 秒")
            } catch (error: Exception) {
                val reason = if (error is io.github.mangi.eta.agent.voice.SpeechFailure) {
                    error.userMessage
                } else {
                    error.message ?: error.javaClass.simpleName
                }
                android.util.Log.e("EtaVoiceClone", "reference import failed: $reason", error)
                report("参考音频导入失败：" + reason, isError = true)
            } finally {
                busy = false
            }
        }
    }

    Column {
        SpeechGroupCard(title = "语音克隆（实验）") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "用一段音频的音色来朗读。模型权重不进 APK，需要单独导入；" +
                        "合成在本机 CPU 上跑，句子越长越慢。",
                    style = MiuixTheme.textStyles.body2,
                    color = secondary,
                )
                Spacer(Modifier.height(10.dp))
                CloneRow("模型", if (modelReady) "已就绪" else "未导入", secondary)
                CloneRow("参考音频", if (hasReference) "已就绪" else "未导入", secondary)
                Spacer(Modifier.height(10.dp))
                CloneAction(if (busy) "处理中…" else "导入模型文件夹", enabled = !busy) {
                    pickModel.launch(null)
                }
                Spacer(Modifier.height(8.dp))
                CloneAction("导入参考音频", enabled = !busy && !recording) {
                    pickReference.launch(arrayOf("audio/*"))
                }
                Spacer(Modifier.height(8.dp))
                CloneAction(
                    if (recording) "停止录音（已录 ${referenceSeconds}s）" else "录制参考音频",
                    enabled = !busy,
                ) {
                    if (recording) {
                        recorder?.finish()
                        return@CloneAction
                    }
                    val capture = io.github.mangi.eta.agent.voice.SpeechRecorder()
                    recorder = capture
                    recording = true
                    referenceSeconds = 0
                    report("正在录音，说几句话后点「停止录音」")
                    scope.launch {
                        val buffer = java.io.ByteArrayOutputStream()
                        val collector = launch(Dispatchers.IO) {
                            for (chunk in capture.frames) buffer.write(chunk)
                        }
                        try {
                            capture.record(onReady = {}, onLevel = {})
                        } catch (error: Exception) {
                            report("录音失败：" + (error.message ?: "未知原因"), isError = true)
                        } finally {
                            runCatching { capture.finish() }
                            collector.join()
                            val bytes = buffer.toByteArray()
                            if (bytes.size >= 32_000) {
                                val (_, seconds) = CloneVoiceStore.saveRecordedReference(context, bytes)
                                referenceSeconds = seconds
                                revision++
                                report("已录制 $seconds 秒参考音频")
                            } else if (bytes.isNotEmpty()) {
                                report("录音太短，至少说两秒", isError = true)
                            }
                            recording = false
                            recorder = null
                        }
                    }
                }
            }
        }

        SpeechGroupCard(title = "参考与试听") {
            SpeechFieldColumn {
                SpeechField(
                    label = "参考音频里说的话（必填，必须是这段音频的原文）",
                    value = referenceText,
                    singleLine = false,
                    onChange = {
                        referenceText = it
                        CloneVoiceStore.saveReferenceText(context, it)
                    },
                )
                SpeechField(
                    label = "要合成的文本",
                    value = text,
                    singleLine = false,
                    onChange = { text = it },
                )
                Spacer(Modifier.height(8.dp))
                CloneAction(if (busy) "合成中…" else "合成并播放", enabled = !busy) {
                    scope.launch {
                        busy = true
                        report("正在加载模型…")
                        try {
                            val samples = withContext(Dispatchers.IO) {
                                val reference = CloneVoiceStore.loadReference(context)
                                if (reference.isEmpty()) {
                                    throw IllegalStateException("还没有参考音频")
                                }
                                CloneVoiceEngine.open(context, CloneVoiceStore.modelDir(context)).use { engine ->
                                    report("正在合成…")
                                    val audio = engine.synthesize(
                                        text = text,
                                        reference = reference,
                                        referenceRate = CloneVoiceStore.SAMPLE_RATE,
                                        referenceText = referenceText,
                                    )
                                    audio to engine.sampleRate
                                }
                            }
                            report("合成完成，时长约 ${samples.first.size / samples.second} 秒")
                            CloneVoicePlayback.play(samples.first, samples.second)
                        } catch (error: Exception) {
                            // SpeechFailure 的 message 只是错误码名，真正原因在 userMessage。
                            val reason = if (error is io.github.mangi.eta.agent.voice.SpeechFailure) {
                                error.userMessage
                            } else {
                                error.message ?: error.javaClass.simpleName
                            }
                            android.util.Log.e("EtaVoiceClone", "synthesize failed: $reason", error)
                            report(reason, isError = true)
                        } finally {
                            busy = false
                        }
                    }
                }
                status?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 10.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = if (statusIsError) Color(0xFFC0392B) else accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloneRow(label: String, value: String, secondary: Color) {
    androidx.compose.foundation.layout.Row(
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
private fun CloneAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
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