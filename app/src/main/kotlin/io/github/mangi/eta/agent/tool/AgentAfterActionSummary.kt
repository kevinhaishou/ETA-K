package io.github.mangi.eta.agent.tool

import io.github.mangi.eta.agent.device.RootShellDeviceController.ElementObservation
import io.github.mangi.eta.agent.device.RootShellDeviceController.Observation
import io.github.mangi.eta.agent.device.RootShellDeviceController.UiNode
import io.github.mangi.eta.agent.device.ScreenObservationMetadata
import org.json.JSONArray
import org.json.JSONObject

/**
 * 动作后观察的模型投影：先给结论（界面是否变化、是否切换应用或窗口），再给新的节点列表。
 * 变化判定只比较节点的可见语义（文本、描述、类名、view_id、位置），不依赖时间戳或动画帧。
 */
internal object AgentAfterActionSummary {
    /** 增量里每侧最多列出的文本条数，控制每步的 token 增量。 */
    private const val PROGRESS_TEXT_LIMIT = 5
    private const val PROGRESS_TEXT_CHARS = 40

    fun build(before: ElementObservation?, observation: Observation): JSONObject {
        val metadata = JSONObject(observation.content)
        val after = observation.elementObservation
        val nodes = after?.nodes.orEmpty()
        val status = metadata.optString("ui_tree_status").ifBlank {
            when {
                after == null -> "query_failed"
                nodes.isEmpty() -> "empty"
                else -> "available"
            }
        }
        val changed = if (status == "available" && nodes.isNotEmpty()) {
            before == null || signature(before.nodes) != signature(nodes)
        } else {
            null
        }
        val note = if (changed == null) {
            metadata.optString("ui_tree_note").ifBlank {
                ScreenObservationMetadata.uiTreeNote(status, observation.image != null)
            }
        } else if (changed) {
            "以上为动作后的新界面：先看 goal_progress 的 added_texts / removed_texts 判断是否朝目标推进，" +
                "再决定下一步；可直接用该 observation_id 继续操作，需要更多节点或截图时再调用 observe_screen"
        } else {
            "界面没有可见变化，动作可能未生效；请换目标或方式，不要原样重复"
        }
        return JSONObject()
            .put("observation_id", after?.id ?: JSONObject.NULL)
            .put("observation_source", after?.source?.wireName ?: metadata.opt("observation_source") ?: JSONObject.NULL)
            .put("package", after?.packageName ?: metadata.optJSONObject("focus")?.optString("package").orEmpty())
            .put("screen_changed", changed ?: JSONObject.NULL)
            .put(
                "package_changed",
                if (after != null) before != null && before.packageName != after.packageName else JSONObject.NULL,
            )
            .put(
                "window_changed",
                if (after != null) before != null && before.windowId != after.windowId else JSONObject.NULL,
            )
            .put("ui_tree_status", status)
            // 目标级进度：只说"变了"不够，还要说"往目标推进了多少"，避免模型盲目继续。
            .put(
                "goal_progress",
                if (changed == true && before != null && after != null) {
                    goalProgress(before.nodes, after.nodes)
                } else {
                    JSONObject.NULL
                },
            )
            .put("ui_tree_truncated", after?.truncated ?: false)
            .put("ui_nodes", JSONArray().also { array -> nodes.forEach { array.put(it.compactJson()) } })
            .put("note", note)
            .apply {
                metadata.optJSONObject("focus")?.let { put("focus", it) }
                metadata.optString("code").takeIf(String::isNotBlank)?.let { put("code", it) }
            }
    }

    /**
     * 动作前后的界面增量，供模型判断"朝目标推进了多少"，而不是只知道界面变了没变。
     *
     * 只比较有可读文本的节点（text/desc 非空）：容器、装饰节点的小位移是噪音。
     * 输出保持精简（文本各最多 [PROGRESS_TEXT_LIMIT] 条、每条截断），避免每步都涨 token。
     */
    private fun goalProgress(beforeNodes: List<UiNode>, afterNodes: List<UiNode>): JSONObject {
        fun identity(node: UiNode) = "${node.className}|${node.viewId}|${node.text}|${node.desc}"
        fun readable(node: UiNode) = node.text.isNotBlank() || node.desc.isNotBlank()
        fun label(node: UiNode) = node.text.ifBlank { node.desc }.trim().take(PROGRESS_TEXT_CHARS)

        val beforeKeys = beforeNodes.mapTo(HashSet()) { identity(it) }
        val afterKeys = afterNodes.mapTo(HashSet()) { identity(it) }
        val added = afterNodes.filter { readable(it) && identity(it) !in beforeKeys }
        val removed = beforeNodes.filter { readable(it) && identity(it) !in afterKeys }
        val checkedBefore = beforeNodes.count { it.checked == true }
        val checkedAfter = afterNodes.count { it.checked == true }

        return JSONObject()
            .put("node_delta", afterNodes.size - beforeNodes.size)
            .put("added_count", added.size)
            .put("removed_count", removed.size)
            .put("checked_delta", checkedAfter - checkedBefore)
            .put(
                "added_texts",
                JSONArray().also { array -> added.take(PROGRESS_TEXT_LIMIT).forEach { array.put(label(it)) } },
            )
            .put(
                "removed_texts",
                JSONArray().also { array -> removed.take(PROGRESS_TEXT_LIMIT).forEach { array.put(label(it)) } },
            )
    }

    private fun signature(nodes: List<UiNode>): List<String> =
        nodes.map { "${it.className}|${it.viewId}|${it.text}|${it.desc}|${it.bounds.toShortString()}" }

    /** 精简字段：省略默认值为 false 的布尔与包名，降低每步附带观察的 token 开销。 */
    private fun UiNode.compactJson(): JSONObject = JSONObject()
        .put("index", index)
        .apply {
            if (text.isNotBlank()) put("text", text)
            if (desc.isNotBlank()) put("desc", desc)
            put("class", className.substringAfterLast('.'))
            if (viewId.isNotBlank()) put("view_id", viewId.substringAfterLast('/'))
            put("center", JSONObject().put("x", centerX).put("y", centerY))
            if (clickable) put("clickable", true)
            if (longClickable) put("long_clickable", true)
            if (scrollable) put("scrollable", true)
            if (editable) put("editable", true)
            if (focused) put("focused", true)
            if (password) put("password", true)
            if (!enabled) put("enabled", false)
            checked?.let { put("checked", it) }
            if (selected) put("selected", true)
            if (hint.isNotBlank()) put("hint", hint)
        }
}
