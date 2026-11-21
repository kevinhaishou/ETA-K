package io.github.mangi.eta.agent.voice.offline

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import io.github.mangi.eta.agent.voice.SpeechErrorCode
import io.github.mangi.eta.agent.voice.SpeechFailure
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 本机离线识别进程（:asr）。
 *
 * 识别放在独立进程：模型加载会吃掉几百 MiB，崩也只崩这一个进程，不牵连聊天。
 * 离线失败直接回报错误，不上传音频、不回落云端，是否改用在线服务由用户在设置里决定。
 *
 * 模型按「短时保留」处理：识别完继续驻留 [IDLE_RELEASE_MS]，期间再次识别不重新加载；
 * 空闲超时或系统内存压力下释放，不做常驻。
 */
class OfflineAsrService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wavPath = intent?.getStringExtra(EXTRA_WAV)
        val modelPath = intent?.getStringExtra(EXTRA_MODEL)
        val tokensPath = intent?.getStringExtra(EXTRA_TOKENS)
        @Suppress("DEPRECATION")
        val receiver = intent?.getParcelableExtra(EXTRA_RECEIVER) as? ResultReceiver
        if (wavPath == null || modelPath == null || tokensPath == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        cancelIdleRelease()
        Thread({
            val bundle = Bundle()
            try {
                val engine = engineFor(modelPath, tokensPath)
                val wav = File(wavPath).readBytes()
                val pcm = if (wav.size > WAV_HEADER) wav.copyOfRange(WAV_HEADER, wav.size) else wav
                bundle.putString(RESULT_TEXT, engine.transcribe(pcm16ToFloat(pcm)))
            } catch (error: Throwable) {
                bundle.putString(RESULT_ERROR, error.message ?: "本机识别失败")
            }
            runCatching { receiver?.send(0, bundle) }
            File(wavPath).delete()
            handler.post { scheduleIdleRelease() }
        }, "offline-asr").start()
        return START_NOT_STICKY
    }

    override fun onTrimMemory(level: Int) {
        // 系统内存吃紧时立刻放开模型，下次识别再重新加载。
        releaseEngine()
        super.onTrimMemory(level)
    }

    override fun onDestroy() {
        releaseEngine()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_WAV = "io.github.kevinhaishou.etak.voice.offline.WAV"
        private const val EXTRA_MODEL = "io.github.kevinhaishou.etak.voice.offline.MODEL"
        private const val EXTRA_TOKENS = "io.github.kevinhaishou.etak.voice.offline.TOKENS"
        private const val EXTRA_RECEIVER = "io.github.kevinhaishou.etak.voice.offline.RECEIVER"
        private const val RESULT_TEXT = "io.github.kevinhaishou.etak.voice.offline.TEXT"
        private const val RESULT_ERROR = "io.github.kevinhaishou.etak.voice.offline.ERROR"
        private const val WAV_HEADER = 44
        private const val IDLE_RELEASE_MS = 60_000L

        private val handler = Handler(Looper.getMainLooper())
        private var engine: SenseVoiceOnnxEngine? = null
        private var engineKey: String? = null
        private var idleRelease: Runnable? = null

        private fun engineFor(modelPath: String, tokensPath: String): SenseVoiceOnnxEngine {
            val key = "$modelPath|$tokensPath"
            engine?.let { if (engineKey == key) return it }
            releaseEngine()
            return SenseVoiceOnnxEngine(modelPath, tokensPath).also {
                engine = it
                engineKey = key
            }
        }

        private fun releaseEngine() {
            runCatching { engine?.close() }
            engine = null
            engineKey = null
        }

        private fun cancelIdleRelease() {
            idleRelease?.let { handler.removeCallbacks(it) }
            idleRelease = null
        }

        private fun scheduleIdleRelease() {
            cancelIdleRelease()
            val runnable = Runnable { releaseEngine() }
            idleRelease = runnable
            handler.postDelayed(runnable, IDLE_RELEASE_MS)
        }

        /** 把整段录音交给独立进程识别；音频走临时文件，避免 Binder 事务上限。 */
        suspend fun transcribe(context: Context, wav: ByteArray, model: File, tokens: File): String =
            suspendCancellableCoroutine { continuation ->
                val cacheFile = File(context.cacheDir, "offline-asr-input.wav")
                cacheFile.writeBytes(wav)
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (!continuation.isActive) return
                        val error = resultData?.getString(RESULT_ERROR)
                        if (error != null) {
                            continuation.resumeWithException(
                                SpeechFailure(SpeechErrorCode.PROTOCOL, error),
                            )
                        } else {
                            continuation.resume(resultData?.getString(RESULT_TEXT).orEmpty())
                        }
                    }
                }
                val intent = Intent(context, OfflineAsrService::class.java).apply {
                    putExtra(EXTRA_WAV, cacheFile.absolutePath)
                    putExtra(EXTRA_MODEL, model.absolutePath)
                    putExtra(EXTRA_TOKENS, tokens.absolutePath)
                    putExtra(EXTRA_RECEIVER, receiver)
                }
                runCatching { context.startService(intent) }.onFailure {
                    throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "本机识别服务启动被系统拦截")
                }
                continuation.invokeOnCancellation {
                    runCatching { context.stopService(Intent(context, OfflineAsrService::class.java)) }
                }
            }
    }
}