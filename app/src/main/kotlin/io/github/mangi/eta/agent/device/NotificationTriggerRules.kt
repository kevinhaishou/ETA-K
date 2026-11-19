package io.github.mangi.eta.agent.device

import android.content.Context
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * 通知触发自动化规则：来源包名与标题/内容关键词都命中时，把通知转成
 * 动作 prompt 提交给 agent。
 *
 * 匹配语义：
 * - [packageName] / [titleKeyword] / [textKeyword] 为空时表示不限制该维度；
 * - 非空时用包含匹配（忽略大小写），不使用正则，避免用户误写导致永不触发；
 * - 三个维度是 AND 关系，全部满足才算命中。
 */
internal data class NotificationTriggerRule(
    val id: String,
    val name: String,
    val packageName: String = "",
    val titleKeyword: String = "",
    val textKeyword: String = "",
    val actionPrompt: String,
    val enabled: Boolean = true,
) {
    fun matches(sbn: StatusBarNotification): Boolean {
        if (!enabled) return false
        if (packageName.isNotBlank() && sbn.packageName != packageName.trim()) return false
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (titleKeyword.isNotBlank() && !title.contains(titleKeyword.trim(), ignoreCase = true)) return false
        if (textKeyword.isNotBlank() && !text.contains(textKeyword.trim(), ignoreCase = true)) return false
        return true
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("package_name", packageName)
        .put("title_keyword", titleKeyword)
        .put("text_keyword", textKeyword)
        .put("action_prompt", actionPrompt)
        .put("enabled", enabled)

    companion object {
        fun fromJson(json: JSONObject): NotificationTriggerRule = NotificationTriggerRule(
            id = json.optString("id"),
            name = json.optString("name"),
            packageName = json.optString("package_name"),
            titleKeyword = json.optString("title_keyword"),
            textKeyword = json.optString("text_keyword"),
            actionPrompt = json.optString("action_prompt"),
            enabled = json.optBoolean("enabled", true),
        )
    }
}

/**
 * 规则存储：SharedPreferences + JSON 数组。规则数量预期是个位数，
 * 不值得上 Room；每次读写全量序列化，实现简单且足够快。
 */
internal object NotificationTriggerStore {
    private const val PREFS = "notification_trigger_rules"
    private const val KEY_RULES = "rules"

    fun load(context: Context): List<NotificationTriggerRule> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            runCatching { NotificationTriggerRule.fromJson(array.getJSONObject(index)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, rules: List<NotificationTriggerRule>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray()
        rules.forEach { rule -> array.put(rule.toJson()) }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    /** 返回命中的规则；来源与关键词全部满足才命中。 */
    fun match(context: Context, sbn: StatusBarNotification): List<NotificationTriggerRule> =
        load(context).filter { rule -> rule.matches(sbn) }
}
