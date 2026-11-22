package io.github.mangi.eta.agent.device

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.mangi.eta.agent.voice.EtaAssistantOverlayService
import io.github.mangi.eta.data.repository.NotificationHistoryRepository

class AgentNotificationHistoryService : NotificationListenerService() {
    private val repository by lazy { NotificationHistoryRepository(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        record(sbn)
        // 通知触发自动化：命中规则的记录转成动作 prompt 提交给 agent 浮层。
        // 放在 record 之后，保证历史里有这条通知可查。
        val context = this
        runCatching {
            NotificationTriggerStore.match(context, sbn).forEach { rule ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                val prompt = buildString {
                    append("收到通知自动化触发。来源应用：")
                    append(sbn.packageName)
                    append("，通知标题「")
                    append(title.ifBlank { "（无标题）" })
                    append("」，内容「")
                    append(text.ifBlank { "（无内容）" })
                    append("」。请执行以下动作：")
                    append(rule.actionPrompt)
                }
                NotificationTriggerLogStore.append(
                    context,
                    NotificationTriggerLogEntry(
                        at = System.currentTimeMillis(),
                        ruleName = rule.name,
                        packageName = sbn.packageName,
                        title = title,
                        text = text,
                    ),
                )
                EtaAssistantOverlayService.runAutomation(context, rule.name, prompt)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedService = this
        activeNotifications?.forEach(::record)
    }

    override fun onListenerDisconnected() {
        if (connectedService === this) connectedService = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (connectedService === this) connectedService = null
        super.onDestroy()
    }

    private fun record(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        repository.record(
            key = sbn.key,
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            postedAt = sbn.postTime,
        )
    }

    companion object {
        @Volatile
        private var connectedService: AgentNotificationHistoryService? = null

        internal fun currentNotifications(): Array<StatusBarNotification>? {
            val service = connectedService ?: return null
            return try {
                service.activeNotifications
            } catch (_: RuntimeException) {
                null
            }
        }

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
                ?: return false
            return manager.isNotificationListenerAccessGranted(
                ComponentName(context, AgentNotificationHistoryService::class.java),
            )
        }
    }
}
