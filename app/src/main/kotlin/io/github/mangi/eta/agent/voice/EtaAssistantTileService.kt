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
 * 点击必须走 [startActivityAndCollapse]：TileService 属于后台上下文，直接 startActivity
 * 会被系统的后台启动限制静默丢弃，表现为“点了没反应”。
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
        val launch = Intent(this, EtaVoiceAssistActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launch)
        }
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}