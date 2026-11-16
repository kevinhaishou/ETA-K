package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.mangi.eta.agent.voice.offline.OfflineAsrCatalog
import io.github.mangi.eta.agent.voice.offline.OfflineAsrStore
import io.github.mangi.eta.agent.voice.offline.SenseVoiceOnnxEngine
import io.github.mangi.eta.agent.voice.offline.pcm16ToFloat
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

internal data class SpeechInputState(
    val phase: EtaSpeechPhase = EtaSpeechPhase.IDLE,
    val preview: String = "",
    val level: Float = 0f,
    val progress: String = "",
    val error: String? = null,
    val configureAvailable: Boolean = false,
) {
    val active: Boolean get() = phase != EtaSpeechPhase.IDLE
}

internal class SpeechInputController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val automaticEndpoint: Boolean = false,
    private val beforeCapture: () -> Unit = {},
    private val afterCapture: () -> Unit = {},
    private val refiner: SpeechTranscriptRefiner = SpeechTranscriptRefiner(),
    private val createRecorder: () -> SpeechAudioCapture = ::SpeechRecorder,
    private val recognizeStream: suspend (
        SpeechSettings,
        SpeechCredentials,
        ReceiveChannel<ByteArray>,
        () -> Unit,
        (String) -> Unit,
        () -> Unit,
        Int,
    ) -> String = ::recognizeSpeechStream,
    private val recognizeFile: suspend (
        Context,
        SpeechSettings,
        SpeechCredentials,
        ByteArray,
        (String) -> Unit,
    ) -> String = ::recognizeSpeechFile,
    private val onResult: (String) -> Unit,
) {
    private val mutableState = MutableStateFlow(SpeechInputState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var finishRequested = false
    private var job: Job? = null
    private var recorder: SpeechAudioCapture? = null
    private var lease: SpeechAudioLease? = null
    private var capturing = false
    private var systemRecognizer: SpeechRecognizer? = null

    /**
     * [holdToTalk] 表示用户按住说话：松手才结束，期间的停顿不会自动发送。
     * 停顿自动发送只对构造时声明 [automaticEndpoint] 的入口生效，聊天听写始终等待用户确认。
     */
    fun start(settings: SpeechSettings? = null, credentials: SpeechCredentials? = null, holdToTalk: Boolean = false) {
        cancel()
        val session = generation
        mutableState.value = SpeechInputState(phase = EtaSpeechPhase.STARTING, progress = "正在连接")
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                val rawConfig = settings ?: SpeechSettingsRepository.settings()
                if (!rawConfig.recognitionEnabled) return@launch
                val rawSecrets = credentials ?: SpeechSettingsRepository.credentials(context)
                val (config, secrets) = SpeechSettingsRepository.resolveDeepSeekFallback(rawConfig, rawSecrets)
                validateSpeechSettings(config, secrets, synthesis = false)
                // 系统识别由本机服务自己开麦，不抢音频焦点，也不经过录音缓冲。
                if (config.asr == AsrProvider.SYSTEM) {
                    val text = recognizeSystem()
                    if (text.isBlank()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有识别到语音，请重试")
                    val final = refined(session, config.refineTranscript, text)
                    if (generation == session) {
                        cancel()
                        onResult(final)
                    }
                    return@launch
                }
                lease = SpeechAudioLease(context) { cancel() }.also { it.acquire(playback = false) }
                val endpointSilenceMs = if (automaticEndpoint && !holdToTalk) config.autoSendSilenceMs else 0
                val audio = createRecorder()
                recorder = audio
                fun update(transform: (SpeechInputState) -> SpeechInputState) {
                    scope.launch(Dispatchers.Main.immediate) {
                        if (generation == session) mutableState.value = transform(mutableState.value)
                    }
                }
                val text = coroutineScope {
                    var recording: Job? = null
                    val begin = {
                        if (recording == null) {
                            beforeCapture()
                            capturing = true
                            recording = launch {
                                try {
                                    audio.record(
                                        onReady = { update { it.copy(phase = EtaSpeechPhase.LISTENING, progress = "正在聆听，点击完成结束") } },
                                        onLevel = { level -> update { it.copy(level = level) } },
                                    )
                                } finally {
                                    if (generation == session) releaseCapture()
                                }
                                update { it.copy(phase = EtaSpeechPhase.RECOGNIZING, level = 0f, progress = "正在识别") }
                            }
                        }
                        Unit
                    }
                    try {
                        when {
                            config.asr == AsrProvider.QWEN_REALTIME || config.asr == AsrProvider.DOUBAO ->
                                recognizeStream(
                                    config,
                                    secrets,
                                    audio.frames,
                                    begin,
                                    { preview -> update { it.copy(preview = preview) } },
                                    { audio.finish() },
                                    endpointSilenceMs,
                                )
                            config.asr == AsrProvider.OFFLINE -> {
                                val model = OfflineAsrCatalog.find(config.offlineModelId)
                                    ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "还没有选择离线模型")
                                if (!OfflineAsrCatalog.isRunnable(model)) {
                                    throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "这个模型格式还没接入")
                                }
                                val modelFile = OfflineAsrStore.modelFile(context, model.id)
                                    ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "还没有导入模型文件")
                                val tokensFile = OfflineAsrStore.tokensFile(context, model.id)
                                    ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "还没有导入 tokens.txt")
                                begin()
                                val wav = withTimeout(120_000) { collectSpeechAudio(audio.frames) }
                                coroutineContext.ensureActive()
                                if (generation != session) throw CancellationException("Speech session replaced")
                                update { it.copy(phase = EtaSpeechPhase.RECOGNIZING, progress = "正在本机识别", level = 0f) }
                                withContext(Dispatchers.IO) {
                                    val engine = SenseVoiceOnnxEngine(modelFile.absolutePath, tokensFile.absolutePath)
                                    try {
                                        val pcm = if (wav.size > 44) wav.copyOfRange(44, wav.size) else wav
                                        engine.transcribe(pcm16ToFloat(pcm))
                                    } finally {
                                        engine.close()
                                    }
                                }
                            }
                            else -> {
                                begin()
                                val wav = withTimeout(65_000) { collectSpeechAudio(audio.frames) }
                                // 关闭通道可能同步唤醒收集器，旧会话不能在取消传播完成前启动请求。
                                coroutineContext.ensureActive()
                                if (generation != session) throw CancellationException("Speech session replaced")
                                recognizeFile(context, config, secrets, wav) { progress ->
                                    update { it.copy(phase = EtaSpeechPhase.RECOGNIZING, progress = progress) }
                                }
                            }
                        }
                    } finally {
                        audio.cancel()
                        recording?.cancel()
                    }
                }
                if (text.isBlank()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有识别到语音，请重试")
                val final = refined(session, config.refineTranscript, text)
                if (generation == session) {
                    cancel()
                    onResult(final)
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                if (generation == session) {
                    val failure = error.speechFailure()
                    AndroidAgentLogger.warn("Eta speech recognition failed: code=${failure.code} type=${error.javaClass.simpleName}")
                    cancel()
                    mutableState.value = SpeechInputState(
                        error = failure.userMessage,
                        configureAvailable = failure.code == SpeechErrorCode.CONFIGURATION ||
                            failure.code == SpeechErrorCode.CREDENTIALS ||
                            failure.code == SpeechErrorCode.AUTHENTICATION,
                    )
                }
            } finally {
                if (generation == session) cancel()
            }
        }
    }

    /**
     * 本机语音识别：交给系统的 RecognitionService 自己开麦，不再额外录音。
     *
     * 荣耀机器上默认服务是自带的识别服务，免费且不需要任何接口凭据；
     * 识别失败按系统错误码转成同样的 [SpeechFailure]，上层提示与云端路径一致。
     */
    private suspend fun recognizeSystem(): String {
        val recognizer = SystemSpeechRecognizer.create(context)
            ?: throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "本机没有可用的语音识别服务")
        systemRecognizer = recognizer
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        mutableState.value = mutableState.value.copy(
                            phase = EtaSpeechPhase.LISTENING,
                            progress = "正在聆听，点击完成结束",
                        )
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) {
                        val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        mutableState.value = mutableState.value.copy(level = level)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        mutableState.value = mutableState.value.copy(
                            phase = EtaSpeechPhase.RECOGNIZING, progress = "正在识别", level = 0f,
                        )
                    }

                    override fun onError(error: Int) {
                        if (continuation.isActive) continuation.resumeWithException(systemAsrFailure(error))
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (continuation.isActive) continuation.resume(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) mutableState.value = mutableState.value.copy(preview = text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                recognizer.startListening(intent)
                continuation.invokeOnCancellation { runCatching { recognizer.cancel() } }
            }
        } finally {
            systemRecognizer = null
            runCatching { recognizer.destroy() }
        }
    }

    private fun systemAsrFailure(error: Int): SpeechFailure = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            SpeechFailure(SpeechErrorCode.PERMISSION, "请允许 Eta 使用麦克风")
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            SpeechFailure(SpeechErrorCode.NETWORK, "本机识别服务暂时不可用，请稍后重试")
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            SpeechFailure(SpeechErrorCode.NO_SPEECH, "没有识别到语音，请重试")
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            SpeechFailure(SpeechErrorCode.AUDIO, "识别服务正忙，请稍后重试")
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_CLIENT ->
            SpeechFailure(SpeechErrorCode.SERVER, "系统语音识别出错了")
        else -> SpeechFailure(SpeechErrorCode.PROTOCOL, "系统语音识别失败（$error）")
    }

    /** 纠错失败不影响交付；只有纠错仍属于当前会话时才展示进度。 */
    private suspend fun refined(session: Long, refineTranscript: Boolean, text: String): String {
        if (!refineTranscript || generation != session) return text
        mutableState.value = mutableState.value.copy(
            phase = EtaSpeechPhase.RECOGNIZING, preview = text, progress = "正在校对", level = 0f,
        )
        return try {
            refiner.refine(text)
        } catch (error: Exception) {
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            val failure = error.speechFailure()
            AndroidAgentLogger.warn("Eta speech refine skipped: code=${failure.code} type=${error.javaClass.simpleName}")
            text
        }
    }

    fun finish() {
        if (!mutableState.value.active || finishRequested) return
        finishRequested = true
        // 系统识别器自己持有麦克风，结束只能靠 stopListening，它随后回调 onResults。
        val system = systemRecognizer
        if (system != null) {
            runCatching { system.stopListening() }
            mutableState.value = mutableState.value.copy(
                phase = EtaSpeechPhase.RECOGNIZING, progress = "正在识别", level = 0f,
            )
            return
        }
        if (mutableState.value.phase == EtaSpeechPhase.STARTING) { cancel(); return }
        recorder?.finish()
        mutableState.value = mutableState.value.copy(phase = EtaSpeechPhase.RECOGNIZING, progress = "正在识别", level = 0f)
    }

    fun cancel() {
        generation++
        finishRequested = false
        // 先取消识别协程，避免关闭录音通道被当作正常录音完成而提交请求。
        job?.cancel(); job = null
        systemRecognizer?.let { runCatching { it.cancel() } }
        systemRecognizer = null
        recorder?.cancel(); recorder = null
        releaseCapture()
        lease?.close(); lease = null
        mutableState.value = SpeechInputState()
    }

    private fun releaseCapture() {
        if (capturing) afterCapture()
        capturing = false
    }
}
