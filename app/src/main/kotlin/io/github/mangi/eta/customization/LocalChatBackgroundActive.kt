package io.github.mangi.eta.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 全局聊天背景是否生效。为 true 时页面容器降为半透明，让背景图透出。
 */
internal val LocalChatBackgroundActive = compositionLocalOf { false }
/** 有背景图时把页面容器涂成透明，让全局背景透出。 */
@Composable
internal fun pageBackgroundColor(base: Color): Color =
    if (LocalChatBackgroundActive.current) Color.Transparent else base
