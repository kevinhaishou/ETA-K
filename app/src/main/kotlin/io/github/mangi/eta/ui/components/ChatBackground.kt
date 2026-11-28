package io.github.mangi.eta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import io.github.mangi.eta.customization.CustomizationStore
import io.github.mangi.eta.customization.LocalChatBackgroundActive
import io.github.mangi.eta.customization.LocalSidebarAlpha
import io.github.mangi.eta.customization.WallpaperScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聊天页背景：用户自定义图片 + 可调遮罩浓度。
 *
 * 作为铺满父容器的底层绘制，调用方把聊天内容叠在它之后即可。
 */
@Composable
internal fun ChatBackground(modifier: Modifier = Modifier) {
    val revision by CustomizationStore.revision.collectAsState()
    val image by produceState<ImageBitmap?>(initialValue = null, revision) {
        value = withContext(Dispatchers.IO) { CustomizationStore.wallpaperBitmap() }
    }
    val chatDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val dim = remember(revision, chatDark) { CustomizationStore.effectiveWallpaperDim(chatDark) }
    val enabled = remember(revision) { CustomizationStore.wallpaperEnabled() }
    val scale = remember(revision) { CustomizationStore.wallpaperScale() }
    val bitmap = image
    if (enabled && bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = if (scale == WallpaperScale.FIT) ContentScale.Fit else ContentScale.Crop,
        )
        if (dim > 0f) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim)),
            )
        }
    }
}

/**
 * 全局背景容器：有背景图时先铺背景，再让整棵 UI 树在其上以半透明容器渲染。
 */
@Composable
internal fun ChatBackgroundScaffold(content: @Composable () -> Unit) {
    val revision by CustomizationStore.revision.collectAsState()
    val active = remember(revision) {
        CustomizationStore.hasWallpaper() && CustomizationStore.wallpaperEnabled()
    }
    val sidebarAlpha = remember(revision) { CustomizationStore.sidebarAlpha() }
    CompositionLocalProvider(LocalSidebarAlpha provides sidebarAlpha) {
        if (active) {
            Box(modifier = Modifier.fillMaxSize()) {
                ChatBackground()
                CompositionLocalProvider(LocalChatBackgroundActive provides true) {
                    content()
                }
            }
        } else {
            content()
        }
    }
}

/** 供页面用 drawBehind 自绘背景：读取解码后的位图（带缓存）。 */
@Composable
internal fun rememberWallpaperBitmap(): ImageBitmap? {
    val revision by CustomizationStore.revision.collectAsState()
    val image by produceState<ImageBitmap?>(initialValue = null, revision) {
        value = withContext(Dispatchers.IO) { CustomizationStore.wallpaperBitmap() }
    }
    val enabled = remember(revision) { CustomizationStore.wallpaperEnabled() }
    return if (enabled) image else null
}

/** 在任意 DrawScope 中按缩放方式绘制壁纸（铺满裁剪 / 适应留边居中）。 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWallpaperBitmap(
    bitmap: ImageBitmap,
    scale: WallpaperScale,
) {
    val widthScale = size.width / bitmap.width
    val heightScale = size.height / bitmap.height
    val factor = if (scale == WallpaperScale.FIT) {
        minOf(widthScale, heightScale)
    } else {
        maxOf(widthScale, heightScale)
    }
    val drawWidth = bitmap.width * factor
    val drawHeight = bitmap.height * factor
    drawImage(
        image = bitmap,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
        dstOffset = androidx.compose.ui.unit.IntOffset(
            ((size.width - drawWidth) / 2f).toInt(),
            ((size.height - drawHeight) / 2f).toInt(),
        ),
        dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt()),
    )
}
