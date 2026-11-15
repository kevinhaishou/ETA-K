package io.github.mangi.eta.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.mangi.eta.customization.CustomizationStore
import io.github.mangi.eta.customization.LocalChatBackgroundActive
import io.github.mangi.eta.customization.LocalSidebarAlpha
import io.github.mangi.eta.customization.WallpaperScale
import java.io.File
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
        value = withContext(Dispatchers.IO) { loadWallpaperBitmap() }
    }
    val dim = remember(revision) { CustomizationStore.wallpaperDim() }
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

/** 按最长边降采样解码，避免用户选的大图整张进内存。 */
private fun decodeSampled(file: File, targetMaxEdge: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest > 0 && longest / sample > targetMaxEdge) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
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

// 多个页面实例共享同一张位图，避免每页各自解码大图
private var cachedWallpaperKey: String? = null
private var cachedWallpaperBitmap: ImageBitmap? = null

private fun loadWallpaperBitmap(): ImageBitmap? {
    val file = CustomizationStore.wallpaperFile() ?: return null
    val blur = CustomizationStore.wallpaperBlur()
    val key = "${file.absolutePath}#$blur"
    if (key == cachedWallpaperKey) return cachedWallpaperBitmap
    val decoded = runCatching {
        val raw = decodeSampled(file) ?: return@runCatching null
        (if (blur > 0f) downUpBlur(raw, blur) else raw).asImageBitmap()
    }.getOrNull()
    cachedWallpaperKey = key
    cachedWallpaperBitmap = decoded
    return decoded
}

/**
 * 廉价模糊：先按半径降采样再用双线性放大，一次性完成。
 * 相比每帧对全屏图做 RenderEffect 模糊，绘制阶段不再有额外开销。
 */
private fun downUpBlur(bitmap: Bitmap, radiusDp: Float): Bitmap {
    val factor = (1f + radiusDp / 3f).coerceIn(1f, 16f)
    val width = (bitmap.width / factor).toInt().coerceAtLeast(1)
    val height = (bitmap.height / factor).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
    return Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
}
