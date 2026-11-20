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
        val raw = prefs.getString(KEY_RULES, null)
            // 首次进入且从未写过：预置几条常见规则（默认关闭），用户按需开启即可。
            ?: return builtInRules()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            runCatching { NotificationTriggerRule.fromJson(array.getJSONObject(index)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /**
     * 内置规则模板：默认全部关闭，避免未经用户同意就执行动作。
     * 只在从未保存过任何规则时返回，用户一旦保存就完全以存储内容为准。
     */
    fun builtInRules(): List<NotificationTriggerRule> = listOf(
        NotificationTriggerRule(
            id = "builtin-wechat-summary",
            name = "微信消息摘要",
            packageName = "com.tencent.mm",
            actionPrompt = "看一眼这条通知说的是什么，用一句话概括，不要回复对方。",
            enabled = false,
        ),
        NotificationTriggerRule(
            id = "builtin-sms-code",
            name = "验证码读出",
            packageName = "",
            textKeyword = "验证码",
            actionPrompt = "读出通知里的验证码，只输出数字，不要做其他操作。",
            enabled = false,
        ),
        NotificationTriggerRule(
            id = "builtin-missed-call",
            name = "未接来电提醒",
            packageName = "com.android.phone",
            titleKeyword = "未接",
            actionPrompt = "告诉我谁来过电话，以及通知里的时间。",
            enabled = false,
        ),
        NotificationTriggerRule(
            id = "builtin-express",
            name = "快递到达提醒",
            packageName = "",
            textKeyword = "取件",
            actionPrompt = "提炼通知里的取件码和驿站位置，简洁地告诉我。",
            enabled = false,
        ),
    )

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
