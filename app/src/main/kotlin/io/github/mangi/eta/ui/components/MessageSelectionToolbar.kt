package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.github.mangi.eta.ui.voice.LocalSpeechPlayback
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 正文选字工具条：接管系统 TextToolbar，选中文字后就地提供复制 / 朗读 / 追问。
 *
 * Compose 不暴露选区文本，取选区只能先触发系统的复制动作再从剪贴板读取；
 * 读完立即还原剪贴板，不留下副作用。未选中时这些操作按整条消息处理。
 */
@Composable
internal fun MessageSelectionToolbar(
    messageText: String,
    onQuote: (String?) -> Unit,
    modifier: Modifier = Modifier,
    messageId: String? = null,
    content: @Composable () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val playback = LocalSpeechPlayback.current
    var menuVisible by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var copyRequest by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** 借系统复制动作取出选区；剪贴板原内容随后还原。 */
    fun selectionText(): String? {
        val request = copyRequest ?: return null
        val backup = clipboard.getText()
        if (runCatching { request() }.isFailure) return null
        val copied = clipboard.getText()?.text?.trim().orEmpty()
        if (backup != null) {
            @Suppress("DEPRECATION")
            clipboard.setText(backup)
        }
        return copied.takeIf { it.isNotEmpty() && it != messageText.trim() }
    }

    val toolbar = remember(clipboard) {
        object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {
                status = TextToolbarStatus.Shown
                menuOffset = IntOffset(rect.left.toInt(), rect.top.toInt())
                copyRequest = onCopyRequested
                menuVisible = true
            }

            override fun hide() {
                status = TextToolbarStatus.Hidden
                menuVisible = false
            }
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(modifier = modifier) {
            content()
            if (menuVisible) {
                Popup(
                    offset = menuOffset,
                    onDismissRequest = { toolbar.hide() },
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ToolbarItem("复制") {
                            // 交给系统复制选中内容；没有选区时退回整条消息。
                            if (copyRequest != null) {
                                runCatching { copyRequest?.invoke() }
                            } else {
                                @Suppress("DEPRECATION")
                                clipboard.setText(AnnotatedString(messageText))
                            }
                            toolbar.hide()
                        }
                        if (playback != null && messageId != null) {
                            ToolbarItem("朗读") {
                                val selected = selectionText()
                                toolbar.hide()
                                playback.speak(messageId, selected ?: messageText)
                            }
                        }
                        ToolbarItem("追问") {
                            val selected = selectionText()
                            toolbar.hide()
                            onQuote(selected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurface,
    )
}