package io.github.mangi.eta.ui.screens.imagegen

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/**
 * 兼容层：上游生图界面基于 Miuix 0.9.5 的玻璃组件编写，
 * 而本项目仍停留在 0.9.x 早期版本。这里用现有组件提供等价入口，
 * 避免为了一个设置页去整体升级 Miuix（那会波及全局外观）。
 */

/** 0.9.5 的玻璃浮层背景句柄；本项目没有该机制，返回 null。 */
@Composable
internal fun currentGlassOverlayBackdrop(): Any? = null

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
