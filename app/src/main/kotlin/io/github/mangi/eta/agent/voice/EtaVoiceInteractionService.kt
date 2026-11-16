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
 * 助手入口桥接：磁贴、锁屏按键等外部入口统一走这里，直接拉起入口浮层。
 * 之前放在 :voice 进程里且依赖会话实例，应用进程被回收时冷启动会失败，表现为“点了没反应”。
 */
class EtaVoiceAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 这个入口在主进程运行：拿不到 :voice 进程里的会话实例，
        // 直接拉起入口浮层，冷启动（应用进程已被回收）时也能出界面。
        EtaAssistantOverlayService.show(this, null)
        finish()
    }
}