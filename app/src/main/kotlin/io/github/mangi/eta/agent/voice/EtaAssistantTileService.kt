package io.github.mangi.eta.agent.voice

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.mangi.eta.R

/**
 * 快捷设置磁贴：一键唤起 Eta 入口浮层。
 *
 * 荣耀 X50 的长按电源键由厂商写死给自家助手，禁用后长按无反应、启用又会抢回默认助手，
 * 所以给一个不依赖按键路由的入口，任何界面下拉通知栏都能用。
 *
 * 启动方式准备了两条：官方的 startActivityAndCollapse 之外再补一条直接 startActivity。
 * MagicOS 上曾观察到 collapse 被静默丢弃——日志里只有 `TileLifecycleManager: onclick`，
 * 既没有 START 也没有 blocked，所以不能只依赖一条通道。
 */
class EtaAssistantTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.eta_assistant_tile)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // 指向主界面而不是透明桥接 Activity：后者没有可见 UI，
        // MagicOS 会把整次启动当作无界面操作静默丢弃。
        val launch = Intent(this, io.github.mangi.eta.ui.MainActivity::class.java)
            .putExtra(EtaAssistantOverlayService.EXTRA_SHOW_ASSISTANT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        android.util.Log.i(TAG, "tile click sdk=" + Build.VERSION.SDK_INT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
            runCatching { startActivityAndCollapse(pending) }
                .onFailure { android.util.Log.w(TAG, "collapse failed: " + it.javaClass.simpleName) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { startActivityAndCollapse(launch) }
                .onFailure { android.util.Log.w(TAG, "collapse failed: " + it.javaClass.simpleName) }
        }
        runCatching { startActivity(launch) }
            .onFailure { android.util.Log.w(TAG, "direct start failed: " + it.javaClass.simpleName) }
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    private companion object {
        const val TAG = "EtaVoice"
    }
}