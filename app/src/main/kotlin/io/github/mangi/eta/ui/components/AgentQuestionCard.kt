package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.tool.AgentQuestionBus
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 会话内提问卡（ask_user）/ 工具审批卡。
 *
 * 挂起期间模型在等这张卡的答案：单选点选项即提交，多选勾选后确认提交，
 * 没有选项或需要补充时用自由输入，也可以跳过。审批卡的「拒绝」以
 * USER_REJECTED 回给模型。
 */
@Composable
internal fun AgentQuestionCard(
    id: String,
    kind: AgentQuestionBus.Kind,
    title: String,
    question: String,
    options: List<String>,
    multiSelect: Boolean,
    toolName: String?,
    toolArgsSummary: String?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isApproval = kind == AgentQuestionBus.Kind.TOOL_APPROVAL
    var selected by remember(id) { mutableStateOf(setOf<String>()) }
    var freeText by remember(id) { mutableStateOf("") }
    val accent = MiuixTheme.colorScheme.primary
    val danger = Color(0xFFC0392B)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isApproval) Icons.Rounded.Security else Icons.Rounded.ContactSupport,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isApproval) danger else accent,
                )
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = question,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (isApproval && !toolArgsSummary.isNullOrBlank()) {
                Text(
                    text = toolArgsSummary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 选项行：单选点选即提交；多选勾选后点「提交」。
            if (options.isNotEmpty() && !isApproval) {
                options.forEach { option ->
                    val checked = option in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (checked) accent.copy(alpha = 0.14f) else MiuixTheme.colorScheme.surface,
                            )
                            .clickable {
                                if (multiSelect) {
                                    selected = if (checked) selected - option else selected + option
                                } else {
                                    onSubmit(option)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (multiSelect && checked) "✓ $option" else option,
                            style = MiuixTheme.textStyles.body1,
                            color = if (checked) accent else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            TextField(
                value = freeText,
                onValueChange = { freeText = it },
                label = if (isApproval) "补充给模型的话（可选）" else "也可以自由输入…",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                QuestionCardButton(
                    text = if (isApproval) "拒绝" else "跳过",
                    container = MiuixTheme.colorScheme.surface,
                    content = if (isApproval) danger else MiuixTheme.colorScheme.onSurface,
                ) {
                    onSubmit("__SKIPPED__")
                }
                when {
                    isApproval -> QuestionCardButton(
                        text = "允许执行",
                        container = accent,
                        content = MiuixTheme.colorScheme.onPrimary,
                    ) {
                        val extra = freeText.trim()
                        onSubmit(if (extra.isBlank()) "允许执行" else "允许执行；$extra")
                    }
                    multiSelect && options.isNotEmpty() -> QuestionCardButton(
                        text = "提交",
                        container = accent,
                        content = MiuixTheme.colorScheme.onPrimary,
                        enabled = selected.isNotEmpty() || freeText.isNotBlank(),
                    ) {
                        onSubmit(buildSelectedAnswer(selected, freeText))
                    }
                    else -> QuestionCardButton(
                        text = "发送",
                        container = accent,
                        content = MiuixTheme.colorScheme.onPrimary,
                        enabled = freeText.isNotBlank(),
                    ) {
                        onSubmit(freeText.trim())
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionCardButton(
    text: String,
    container: Color,
    content: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body1,
        color = if (enabled) content else content.copy(alpha = 0.4f),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) container else container.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun buildSelectedAnswer(selected: Set<String>, freeText: String): String {
    val parts = buildList {
        if (selected.isNotEmpty()) add(selected.joinToString("、"))
        if (freeText.isNotBlank()) add(freeText.trim())
    }
    return parts.joinToString("\n补充：")
}
