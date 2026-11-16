package io.github.mangi.eta.agent.voice

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import io.github.mangi.eta.core.AssistantContextFlags

class EtaVoiceInteractionService : VoiceInteractionService() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.i(TAG, "service onCreate")
    }

    override fun onReady() {
        super.onReady()
        activeService = this
        android.util.Log.i(TAG, "service onReady sdk=" + Build.VERSION.SDK_INT)
        if (Build.VERSION.SDK_INT >= 37) {
            setInvocationEffectEnabled(true)
        }
    }

    override fun onShutdown() {
        if (activeService === this) activeService = null
        android.util.Log.i(TAG, "service onShutdown")
        super.onShutdown()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        android.util.Log.i(TAG, "launch assist from keyguard")
        startActivity(
            Intent(this, EtaVoiceAssistActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun showEtaSession() {
        showSession(Bundle(), AssistantContextFlags.forSdk(Build.VERSION.SDK_INT))
    }

    companion object {
        private const val TAG = "EtaVoice"

        @Volatile
        private var activeService: EtaVoiceInteractionService? = null

        internal fun requestSession(): Boolean {
            val service = activeService ?: return false
            service.showEtaSession()
            return true
        }
    }
}

/**
 * 系统助手入口桥接：优先请求语音会话，服务尚未就绪（冷启动、被系统回收）时直接拉起入口浮窗，
 * 避免按键后没有任何反应。MagicOS 上长按电源键是否走到这里取决于系统助手绑定，实测以日志为准。
 */
class EtaVoiceAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = EtaVoiceInteractionService.requestSession()
        android.util.Log.i("EtaVoice", "assist activity session=" + session)
        if (!session) EtaAssistantOverlayService.show(this, null)
        finish()
    }
}