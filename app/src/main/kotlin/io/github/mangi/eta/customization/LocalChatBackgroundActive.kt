package io.github.mangi.eta.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 全局聊天背景是否生效。为 true 时页面容器降为半透明，让背景图透出。
 */
internal val LocalChatBackgroundActive = compositionLocalOf { false }

/** 会话侧栏不透明度，由 CustomizationStore 驱动，供面板与推移容器共用。 */
internal val LocalSidebarAlpha = compositionLocalOf { 0.25f }
/** 有背景图时把页面容器涂成透明，让全局背景透出。 */
@Composable
internal fun pageBackgroundColor(base: Color): Color =
    if (LocalChatBackgroundActive.current) Color.Transparent else base
