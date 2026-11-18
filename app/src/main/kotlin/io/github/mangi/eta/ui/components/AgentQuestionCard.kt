package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactSupport
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 会话内提问卡 / 工具审批卡。
 *
 * 挂起期间模型在等这张卡的答案；单选点选项即提交，多选勾选后确认提交，
 * 没有选项或需要补充时用自由输入。审批卡的「拒绝」是 destructive。
 */
@Composable
internal fun AgentQuestionCard(
    id: String,
    kind: io.github.mangi.eta.agent.tool.AgentQuestionBus.Kind,
    title: String,
    question: String,
    options: List<String>,
    multiSelect: Boolean,
    toolName: String?,
    toolArgsSummary: String?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isApproval = kind == io.github.mangi.eta.agent.tool.AgentQuestionBus.Kind.TOOL_APPROVAL
    var selected by remember(id) { mutableStateOf(setOf<String>()) }
    var freeText by remember(id) { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isApproval) Icons.Rounded.Security else Icons.Rounded.ContactSupport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isApproval) Color(0xFFC0392B) else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (isApproval && !toolArgsSummary.isNullOrBlank()) {
                Text(
                    text = toolArgsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (options.isEmpty() || isApproval) {
                OutlinedTextField(
                    value = freeText,
                    onValueChange = { freeText = it },
                    placeholder = {
                        Text(
                            text = if (isApproval) "补充给模型的话（可选）" else "也可以自由输入…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (options.isNotEmpty() && !isApproval) {
                options.forEach { option ->
                    val checked = option in selected
                    OutlinedButton(
                        onClick = {
                            selected = if (multiSelect) {
                                if (checked) selected - option else selected + option
                            } else {
                                // 单选点选项即提交，减少一次点击
                                onSubmit(option)
                                return@OutlinedButton
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(
                            text = if (multiSelect && checked) "✓ $option" else option,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                if (isApproval) {
                    OutlinedButton(
                        onClick = { onSubmit("拒绝") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFC0392B),
                        ),
                    ) {
                        Text("拒绝")
                    }
                    Button(
                        onClick = {
                            val extra = freeText.trim()
                            onSubmit(if (extra.isBlank()) "允许执行" else "允许执行；$extra")
                        },
                    ) {
                        Text("允许执行")
                    }
                } else {
                    if (options.isNotEmpty() && multiSelect) {
                        OutlinedButton(
                            onClick = { onSubmit("__SKIPPED__") },
                        ) {
                            Text("跳过")
                        }
                        Button(
                            onClick = { onSubmit(buildSelectedAnswer(selected, freeText)) },
                            enabled = selected.isNotEmpty() || freeText.isNotBlank(),
                        ) {
                            Text("提交")
                        }
                    } else {
                        OutlinedButton(onClick = { onSubmit("__SKIPPED__") }) {
                            Text("跳过")
                        }
                        Button(
                            onClick = { onSubmit(freeText.trim()) },
                            enabled = freeText.isNotBlank(),
                        ) {
                            Text("发送")
                        }
                    }
                }
            }
        }
    }
}

private fun buildSelectedAnswer(selected: Set<String>, freeText: String): String {
    val parts = buildList {
        if (selected.isNotEmpty()) add(selected.joinToString("、"))
        if (freeText.isNotBlank()) add(freeText.trim())
    }
    return parts.joinToString("\n补充：")
}
