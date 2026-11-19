package io.github.mangi.eta.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.NotificationTriggerRule
import io.github.mangi.eta.agent.device.NotificationTriggerStore
import io.github.mangi.eta.ui.SettingsGroupCard
import io.github.mangi.eta.ui.SettingsIcon
import io.github.mangi.eta.ui.SettingsIconColors
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.Switch

/**
 * 通知触发自动化管理：收到匹配规则的通知时，把「通知摘要 + 动作指令」提交给 agent。
 *
 * 规则保存在 SharedPreferences（JSON），条目预期个位数，不引入数据库。
 */
@Composable
internal fun NotificationTriggerSection() {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(NotificationTriggerStore.load(context)) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NotificationTriggerRule?>(null) }

    fun update(next: List<NotificationTriggerRule>) {
        NotificationTriggerStore.save(context, next)
        rules = next
    }

    SettingsGroupCard {
        ArrowPreference(
            title = "通知触发自动化",
            summary = if (rules.isEmpty()) {
                "未配置规则；命中通知时自动提交动作指令"
            } else {
                "${rules.count { it.enabled }} 条规则启用"
            },
            startAction = {
                SettingsIcon(icon = Icons.Rounded.Notifications, tint = SettingsIconColors.Blue)
            },
            onClick = { showEditor = true },
        )
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("通知触发规则") },
            text = {
                Column {
                    if (rules.isEmpty()) {
                        Text("还没有规则。点「添加规则」创建：来源包名与关键词都命中时，通知会转成动作指令提交给 agent。")
                    }
                    rules.forEach { rule ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = rule.name,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        update(rules.map {
                                            if (it.id == rule.id) it.copy(enabled = enabled) else it
                                        })
                                    },
                                )
                                TextButton(onClick = { pendingDelete = rule }) {
                                    Text("删除")
                                }
                            }
                            Text(
                                text = buildString {
                                    append("包名：")
                                    append(rule.packageName.ifBlank { "任意" })
                                    if (rule.titleKeyword.isNotBlank()) {
                                        append(" · 标题含「")
                                        append(rule.titleKeyword)
                                        append("」")
                                    }
                                    if (rule.textKeyword.isNotBlank()) {
                                        append(" · 内容含「")
                                        append(rule.textKeyword)
                                        append("」")
                                    }
                                },
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                text = "动作：${rule.actionPrompt}",
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    TextButton(onClick = { showEditor = false }) {
                        Text("关闭")
                    }
                }
            },
            confirmButton = {},
        )
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除规则「${rule.name}」？") },
            text = { Text("删除后，匹配该规则的通知将不再触发自动化。") },
            confirmButton = {
                TextButton(onClick = {
                    update(rules.filterNot { it.id == rule.id })
                    pendingDelete = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showEditor) {
        RuleEditorDialog(
            onDismiss = { /* 编辑器独立于 showEditor，由自身关闭 */ },
            onSaved = { rule ->
                update(rules + rule)
                showEditor = false
            },
        )
    }
}

@Composable
private fun RuleEditorDialog(onDismiss: () -> Unit, onSaved: (NotificationTriggerRule) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var titleKeyword by remember { mutableStateOf("") }
    var textKeyword by remember { mutableStateOf("") }
    var actionPrompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加通知触发规则") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("来源包名（留空 = 任意应用）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextField(
                    value = titleKeyword,
                    onValueChange = { titleKeyword = it },
                    label = { Text("标题关键词（留空 = 不限）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextField(
                    value = textKeyword,
                    onValueChange = { textKeyword = it },
                    label = { Text("内容关键词（留空 = 不限）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextField(
                    value = actionPrompt,
                    onValueChange = { actionPrompt = it },
                    label = { Text("动作指令（交给 agent 的 prompt）") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    text = "三个匹配条件是「且」关系：全部命中才触发。触发时通知的包名、标题、内容会附在指令前交给 agent。",
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && actionPrompt.isNotBlank(),
                onClick = {
                    onSaved(
                        NotificationTriggerRule(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            packageName = packageName.trim(),
                            titleKeyword = titleKeyword.trim(),
                            textKeyword = textKeyword.trim(),
                            actionPrompt = actionPrompt.trim(),
                        ),
                    )
                    runCatching {
                        android.widget.Toast.makeText(
                            context,
                            "规则已保存",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
