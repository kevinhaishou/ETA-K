package io.github.mangi.eta.customization

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ETA-K 本地定制：自定义系统提示词与聊天背景。
 *
 * 刻意独立于上游的 SettingsDataStore / Prefs，新增配置不修改上游数据类，
 * 上游同步后只需处理极少量调用点的冲突。
 */
internal object CustomizationStore {
    private const val PREFS_NAME = "eta_customization"
    private const val KEY_SYSTEM_PROMPT = "custom_system_prompt"
    private const val KEY_WALLPAPER_NAME = "chat_background_name"
    private const val KEY_WALLPAPER_DIM = "chat_background_dim"
    private const val KEY_WALLPAPER_ENABLED = "chat_background_enabled"
    private const val KEY_WALLPAPER_BLUR = "chat_background_blur"
    private const val KEY_WALLPAPER_SCALE = "chat_background_scale"
    private const val KEY_SIDEBAR_ALPHA = "sidebar_alpha"
    private const val DIR_WALLPAPER = "chat-background"

    const val DEFAULT_DIM = 0.35f
    const val MAX_DIM = 0.85f
    const val MAX_BLUR = 24f
    const val DEFAULT_SIDEBAR_ALPHA = 0.12f

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var appContext: Context? = null

    private val revisionState = MutableStateFlow(0)

    /** 设置变更计数，UI 据此重新读取并重绘。 */
    val revision: StateFlow<Int> = revisionState.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val app = context.applicationContext
            appContext = app
            prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { persisted ->
                persisted.registerOnSharedPreferenceChangeListener { _, _ -> bump() }
            }
        }
    }

    fun systemPrompt(): String = prefs?.getString(KEY_SYSTEM_PROMPT, null).orEmpty()

    fun setSystemPrompt(value: String) {
        val persisted = prefs ?: return
        persisted.edit().putString(KEY_SYSTEM_PROMPT, value).apply()
        bump()
    }

    fun wallpaperDim(): Float =
        (prefs?.getFloat(KEY_WALLPAPER_DIM, DEFAULT_DIM) ?: DEFAULT_DIM).coerceIn(0f, MAX_DIM)

    fun setWallpaperDim(value: Float) {
        val persisted = prefs ?: return
        persisted.edit().putFloat(KEY_WALLPAPER_DIM, value.coerceIn(0f, MAX_DIM)).apply()
        bump()
    }

    fun hasWallpaper(): Boolean = wallpaperFile() != null

    /** 壁纸总开关：关闭后不绘制背景，页面回到不透明外观。 */
    fun wallpaperEnabled(): Boolean = prefs?.getBoolean(KEY_WALLPAPER_ENABLED, true) ?: true

    fun setWallpaperEnabled(value: Boolean) {
        val persisted = prefs ?: return
        persisted.edit().putBoolean(KEY_WALLPAPER_ENABLED, value).apply()
        bump()
    }

    /** 壁纸模糊半径（dp）。 */
    fun wallpaperBlur(): Float =
        (prefs?.getFloat(KEY_WALLPAPER_BLUR, 0f) ?: 0f).coerceIn(0f, MAX_BLUR)

    fun setWallpaperBlur(value: Float) {
        val persisted = prefs ?: return
        persisted.edit().putFloat(KEY_WALLPAPER_BLUR, value.coerceIn(0f, MAX_BLUR)).apply()
        bump()
    }

    /** 壁纸缩放方式：FILL 铺满屏幕（裁剪），FIT 适应屏幕（留边）。 */
    fun wallpaperScale(): WallpaperScale =
        WallpaperScale.entries.firstOrNull {
            it.persistedValue == prefs?.getString(KEY_WALLPAPER_SCALE, null)
        } ?: WallpaperScale.FILL

    fun setWallpaperScale(value: WallpaperScale) {
        val persisted = prefs ?: return
        persisted.edit().putString(KEY_WALLPAPER_SCALE, value.persistedValue).apply()
        bump()
    }

    /** 会话侧栏不透明度。 */
    fun sidebarAlpha(): Float =
        (prefs?.getFloat(KEY_SIDEBAR_ALPHA, DEFAULT_SIDEBAR_ALPHA) ?: DEFAULT_SIDEBAR_ALPHA)
            .coerceIn(0f, 1f)

    fun setSidebarAlpha(value: Float) {
        val persisted = prefs ?: return
        persisted.edit().putFloat(KEY_SIDEBAR_ALPHA, value.coerceIn(0f, 1f)).apply()
        bump()
    }

    fun wallpaperFile(): File? {
        val name = prefs?.getString(KEY_WALLPAPER_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val app = appContext ?: return null
        val file = File(File(app.filesDir, DIR_WALLPAPER), name)
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    /** 把相册里选中的图片复制到私有目录；成功返回 true。 */
    fun importWallpaper(context: Context, uri: Uri): Boolean {
        val persisted = prefs ?: return false
        val directory = File(context.applicationContext.filesDir, DIR_WALLPAPER).apply { mkdirs() }
        val target = File(directory, "wallpaper-${System.currentTimeMillis()}.img")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } != null && target.length() > 0
        }.getOrDefault(false)
        if (!copied) {
            target.delete()
            return false
        }
        directory.listFiles()?.filter { it.name != target.name }?.forEach { it.delete() }
        persisted.edit().putString(KEY_WALLPAPER_NAME, target.name).apply()
        bump()
        return true
    }

    fun clearWallpaper(context: Context) {
        val persisted = prefs ?: return
        persisted.edit().remove(KEY_WALLPAPER_NAME).apply()
        File(context.applicationContext.filesDir, DIR_WALLPAPER).listFiles()?.forEach { it.delete() }
        bump()
    }

    private fun bump() {
        revisionState.value += 1
    }
}

/** 壁纸缩放方式。 */
internal enum class WallpaperScale(val persistedValue: String) {
    FILL("crop"),
    FIT("fit"),
}
