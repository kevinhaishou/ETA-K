package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Miuix 玻璃组件兼容层。
 *
 * 上游的生图 / 定时任务界面基于 Miuix 0.9.5 的玻璃 API 编写，而本项目仍停留在
 * 0.9.x 早期版本。整体升级 Miuix 会波及全局外观（顶栏、侧栏、气泡），收益不匹配，
 * 因此在这里用现有组件提供等价入口，让新功能的界面能用起来。
 */

/** 0.9.5 的玻璃浮层背景句柄；本项目没有该机制，返回 null。 */
@Composable
internal fun currentGlassOverlayBackdrop(): Any? = null

@Composable
internal fun GlassDialog(
    show: Boolean,
    title: String?,
    summary: String? = null,
    backdrop: Any? = null,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return
    WindowDialog(
        show = true,
        title = title.orEmpty(),
        summary = summary.orEmpty(),
        onDismissRequest = onDismissRequest,
        content = content,
    )
}

@Composable
internal fun GlassBottomSheet(
    show: Boolean,
    title: String? = null,
    backdrop: Any? = null,
    insideMargin: DpSize = DpSize(0.dp, 0.dp),
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!show) return
    WindowDialog(
        show = true,
        title = title.orEmpty(),
        onDismissRequest = onDismissRequest,
        content = content,
    )
}

@Composable
internal fun GlassDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Any? = null,
    enabled: Boolean = true,
) {
    OverlayDropdownPreference(
        title = title,
        items = items,
        selectedIndex = selectedIndex,
        enabled = enabled,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/** 0.9.5 的玻璃顶栏图标按钮，用现有 IconButton 承载。 */
@Composable
internal fun MiuixTopBarActionButton(
    icon: ImageVector = Icons.Rounded.Edit,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(modifier = modifier, onClick = onClick) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier)
    }
}

/** 0.9.5 的玻璃图标集中只用到 Edit，这里映射成当前图标库里的等价项。 */
internal object MiuixIconsGlassAlias {
    val Edit: ImageVector get() = Icons.Rounded.Edit
    val Ok: ImageVector get() = Icons.Rounded.Check
    val Add: ImageVector get() = Icons.Rounded.Add
}
