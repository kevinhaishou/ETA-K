package io.github.mangi.eta.agent.voice

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class SpeechPlaybackState(
    val messageId: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val paused: Boolean = false,
    /** 离线朗读按片段推进；0 表示云端整篇播放或尚未开始，界面据此决定是否显示进度。 */
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    /** 当前片段的前 120 字，供朗读面板定位到具体文字。 */
    val segmentText: String = "",
)

internal class SpeechPlaybackController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val beforePlayback: () -> Unit = {},
    private val interruptOnScreenOff: Boolean = true,
    private val afterPlayback: () -> Unit = {},
    private val synthesize: suspend (SpeechSettings, SpeechCredentials, String, (ByteArray) -> Unit) -> Unit = ::synthesizeSpeech,
    /** 整篇单会话流式合成；为 null 时只用逐段请求。 */
    private val streamSynthesize: (suspend (SpeechSettings, SpeechCredentials, List<String>, (ByteArray) -> Unit) -> Unit)? = ::streamSpeech,
    private val createOutput: () -> SpeechAudioOutput = ::SpeechPcmOutput,
) {
    private val mutableState = MutableStateFlow(SpeechPlaybackState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var output: SpeechAudioOutput? = null
    private var lease: SpeechAudioLease? = null
    private var playbackActive = false
    private var systemTts: SystemTtsPlayer? = null
    /** 暂停是一个闸门：已排队的片段停在原地，恢复后从当前片段继续。 */
    private val pausedFlow = MutableStateFlow(false)
    /** 面板上「上一段 / 下一段」的落点；离线循环在每个片段开始前取一次。 */
    private val segmentRequest = MutableStateFlow<Int?>(null)

    fun speak(messageId: String, text: String, settings: SpeechSettings? = null, credentials: SpeechCredentials? = null) {
        stop()
        val session = generation
        mutableState.value = SpeechPlaybackState(messageId, loading = true)
        job = scope.launch(Dispatchers.Main.immediate) {
            try {
                val rawConfig = settings ?: SpeechSettingsRepository.settings()
                val rawSecrets = credentials ?: SpeechSettingsRepository.credentials(context)
                val (config, secrets) = SpeechSettingsRepository.resolveDeepSeekFallback(rawConfig, rawSecrets)
                android.util.Log.w("EtaSystemTts", "branch enter tts=" + config.tts)
                if (config.tts == TtsProvider.ANDROID) {
                    val readableText = withContext(Dispatchers.Default) { SpeechText.readable(text) }
                    if (readableText.isBlank()) {
                        throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "这条消息没有可朗读的正文")
                    }
                    val segments = withContext(Dispatchers.Default) {
                        SpeechText.segments(readableText.take(4000))
                    }
                    val tts = systemTts ?: SystemTtsPlayer(context).also { systemTts = it }
                    // 系统引擎没有 pause：按片段循环，暂停只停当前片段，继续时重播该片段。
                    var index = 0
                    while (index < segments.size) {
                        if (session != generation) return@launch
                        if (pausedFlow.value) pausedFlow.first { !it }
                        if (session != generation) return@launch
                        // 面板跳段：当前片段被打断后在这里落到目标片段，避免跳过整段。
                        segmentRequest.value?.let { requested ->
                            segmentRequest.value = null
                            if (requested in segments.indices) index = requested
                        }
                        publish(messageId, index, segments.size, pausedFlow.value, segments[index].text.take(120))
                        val finished = tts.speak(segments[index].text)
                        if (finished) index++
                    }
                    return@launch
                }
                lease = SpeechAudioLease(context, interruptOnScreenOff) { stop() }
                    .also { it.acquire(playback = false) }
                validateSpeechSettings(config, secrets, synthesis = true)
                val readable = withContext(Dispatchers.Default) { SpeechText.readable(text) }
                if (readable.isBlank()) throw SpeechFailure(SpeechErrorCode.NO_SPEECH, "这条消息没有可朗读的正文")
                if (readable.length > 30_000) throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "正文过长，请选择较短的回答朗读")
                lease?.requestPlaybackFocus { beforePlayback(); playbackActive = true }
                val player = createOutput()
                output = player
                publish(messageId, 0, 0, pausedFlow.value)
                playChunks(config, secrets, SpeechText.chunks(readable), player) {
                    scope.launch(Dispatchers.Main.immediate) {
                        if (session == generation) publish(messageId, 0, 0, pausedFlow.value)
                    }
                }
                player.finish()
                withTimeout(180_000) { while (!player.drained()) delay(20) }
                if (session == generation) stop()
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                if (session == generation) {
                    val failure = error.speechFailure()
                    AndroidAgentLogger.warn("Eta speech playback failed: code=${failure.code} type=${error.javaClass.simpleName}")
                    stop()
                    mutableState.value = SpeechPlaybackState(error = failure.userMessage)
                }
            } finally {
                if (session == generation) stop()
            }
        }
    }

    /** 暂停：离线停当前片段、云端停住音轨；两条路径都保留进度，继续时接上。 */
    fun pause() {
        if (mutableState.value.messageId == null || mutableState.value.paused) return
        pausedFlow.value = true
        systemTts?.pause()
        output?.pause()
        mutableState.value = mutableState.value.copy(paused = true)
    }

    fun resume() {
        if (mutableState.value.messageId == null || !mutableState.value.paused) return
        pausedFlow.value = false
        output?.resume()
        mutableState.value = mutableState.value.copy(paused = false)
    }

    fun togglePause() {
        if (mutableState.value.paused) resume() else pause()
    }

    /** 上一段 / 下一段：离线朗读按片段跳，云端整篇流式播放没有片段可跳。 */
    fun skip(delta: Int) {
        val current = mutableState.value
        if (current.messageId == null || current.segmentCount <= 1) return
        val target = (current.segmentIndex + delta).coerceIn(0, current.segmentCount - 1)
        if (target == current.segmentIndex) return
        segmentRequest.value = target
        // 跳段本身就是「继续听」，暂停闸门一起放开。
        pausedFlow.value = false
        systemTts?.pause()
        mutableState.value = current.copy(segmentIndex = target, paused = false)
    }

    /**
     * 合成按网络速度下载，播放按实时速度写入音轨，两者经队列解耦。若在合成回调里直接写音轨，
     * 读流会被播放节奏拖住，服务端也会长时间等待客户端读取。
     *
     * 优先把整篇放进一个流式合成会话，音频是一条连续的流，整体只在首尾淡入淡出。
     * 流式会话在出声前失败（例如账号未开通实时模型、自定义地址不支持 WebSocket）时，
     * 回落到逐段请求：最多领先播放 [PREFETCH_CHUNKS] 段，接缝处由 [SpeechSegmentFade] 淡入淡出。
     * 已经出声后再失败则直接报错，不从头重读。
     */
    private suspend fun playChunks(
        config: SpeechSettings,
        secrets: SpeechCredentials,
        chunks: List<String>,
        player: SpeechAudioOutput,
        onFirstAudio: () -> Unit,
    ) = coroutineScope {
        val queue = Channel<SpeechQueueItem>(Channel.UNLIMITED)
        val ahead = Semaphore(PREFETCH_CHUNKS)
        launch {
            try {
                var streamed = false
                val stream = streamSynthesize
                if (stream != null && supportsSpeechSession(config.tts)) {
                    try {
                        ahead.acquire()
                        stream(config, secrets, chunks) { bytes ->
                            streamed = true
                            queue.trySend(SpeechQueueItem.Audio(bytes))
                        }
                        queue.send(SpeechQueueItem.ChunkEnd)
                        streamed = true
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                        if (streamed) throw error
                        AndroidAgentLogger.warn("Eta speech stream unavailable, using segments: code=${error.speechFailure().code}")
                        ahead.release()
                    }
                }
                if (!streamed) for (chunk in chunks) {
                    ahead.acquire()
                    synthesize(config, secrets, chunk) { bytes -> queue.trySend(SpeechQueueItem.Audio(bytes)) }
                    queue.send(SpeechQueueItem.ChunkEnd)
                }
                queue.close()
            } catch (error: Throwable) {
                // 合成超时是 CancellationException，子协程以它结束不会取消父作用域，必须经队列交给播放端。
                queue.close(error)
            }
        }
        withContext(Dispatchers.IO) {
            var announced = false
            val fade = SpeechSegmentFade()
            // 音轨写满时 write 会休眠重试；可中断才能让宿主取消立即结束这里，随后关闭音轨。
            suspend fun play(bytes: ByteArray) {
                if (bytes.isNotEmpty()) runInterruptible { player.write(bytes) }
            }
            for (item in queue) when (item) {
                is SpeechQueueItem.Audio -> {
                    if (!announced) { announced = true; onFirstAudio() }
                    play(fade.write(item.bytes))
                }
                SpeechQueueItem.ChunkEnd -> {
                    play(fade.end())
                    ahead.release()
                }
            }
        }
    }

    fun stop() {
        pausedFlow.value = false
        segmentRequest.value = null
        systemTts?.stop()
        generation++
        output?.close(); output = null
        job?.cancel(); job = null
        lease?.close(); lease = null
        if (playbackActive) afterPlayback()
        playbackActive = false
        mutableState.value = SpeechPlaybackState()
    }

    private fun publish(messageId: String, index: Int, count: Int, paused: Boolean, text: String = "") {
        mutableState.value = SpeechPlaybackState(
            messageId = messageId,
            paused = paused,
            segmentIndex = index,
            segmentCount = count,
            segmentText = text,
        )
    }
}

private sealed interface SpeechQueueItem {
    class Audio(val bytes: ByteArray) : SpeechQueueItem
    data object ChunkEnd : SpeechQueueItem
}

private const val PREFETCH_CHUNKS = 2

internal interface SpeechAudioOutput : Closeable {
    fun write(bytes: ByteArray)
    fun finish()
    fun drained(): Boolean

    /** 暂停只是让音轨停住，合成与队列仍可继续；恢复时从停下的位置续播。 */
    fun pause() {}

    fun resume() {}
}