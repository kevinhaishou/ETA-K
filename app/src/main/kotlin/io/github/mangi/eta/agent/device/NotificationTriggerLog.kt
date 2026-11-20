package io.github.mangi.eta.agent.device

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一次通知自动化触发的记录，只保留排障需要的字段。 */
internal data class NotificationTriggerLogEntry(
    val at: Long,
    val ruleName: String,
    val packageName: String,
    val title: String,
    val text: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("at", at)
        .put("rule", ruleName)
        .put("pkg", packageName)
        .put("title", title)
        .put("text", text)

    companion object {
        fun fromJson(json: JSONObject): NotificationTriggerLogEntry = NotificationTriggerLogEntry(
            at = json.optLong("at"),
            ruleName = json.optString("rule"),
            packageName = json.optString("pkg"),
            title = json.optString("title"),
            text = json.optString("text"),
        )
    }
}

/**
 * 触发记录：SharedPreferences + JSON 数组，最新的排在最前。
 *
 * 只记录「命中并提交了动作」的事实，不记录 agent 的执行结果——结果属于会话，
 * 这里再存一份会和会话历史打架。上限 60 条，够回溯最近一段时间。
 */
internal object NotificationTriggerLogStore {
    private const val PREFS = "notification_trigger_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 60

    fun load(context: Context): List<NotificationTriggerLogEntry> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            runCatching { NotificationTriggerLogEntry.fromJson(array.getJSONObject(index)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun append(context: Context, entry: NotificationTriggerLogEntry) {
        runCatching {
            val next = (listOf(entry) + load(context)).take(MAX_ENTRIES)
            val array = JSONArray()
            next.forEach { array.put(it.toJson()) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, array.toString())
                .apply()
        }
    }

    fun clear(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ENTRIES)
                .apply()
        }
    }
}
