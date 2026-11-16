package io.github.mangi.eta.agent.voice

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * 快捷设置磁贴：一键唤起 Eta 入口浮层。
 *
 * 荣耀 X50 的长按电源键由厂商写死给自家助手，禁用后长按无反应、启用又会抢回默认助手，
 * 所以给一个不依赖按键路由的入口，任何界面下拉通知栏都能用。
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
        val launch = Runnable {
            startActivity(
                Intent(this, EtaVoiceAssistActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        // 锁屏上点磁贴需要先解锁，否则系统不会放行启动 Activity。
        if (isLocked) unlockAndRun(launch) else launch.run()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}