package io.github.mangi.eta.customization

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PromptPreset(
    val id: String,
    val name: String,
    val content: String,
)

/**
 * ETA-K 本地定制：自定义系统提示词与聊天背景。
 *
 * 刻意独立于上游的 SettingsDataStore / Prefs，新增配置不修改上游数据类，
 * 上游同步后只需处理极少量调用点的冲突。
 */
internal object CustomizationStore {
    private const val PREFS_NAME = "eta_customization"
    private const val KEY_SYSTEM_PROMPT = "custom_system_prompt"
    private const val KEY_PROMPT_LIBRARY = "prompt_library"
    private const val KEY_PROMPT_ACTIVE = "prompt_active_id"
    private const val KEY_WALLPAPER_NAME = "chat_background_name"
    private const val KEY_WALLPAPER_DIM = "chat_background_dim"
    private const val KEY_WALLPAPER_ENABLED = "chat_background_enabled"
    private const val KEY_WALLPAPER_BLUR = "chat_background_blur"
    private const val KEY_WALLPAPER_SCALE = "chat_background_scale"
    private const val KEY_SIDEBAR_ALPHA = "sidebar_alpha"
    private const val DIR_WALLPAPER = "chat-background"

    const val DEFAULT_DIM = 0.35f
    const val MAX_DIM = 0.85f
    /** 深/浅色主题下的最低可读性压暗，低于此值正文不可读。 */
    const val MIN_READABLE_DIM_DARK = 0.42f
    const val MIN_READABLE_DIM_LIGHT = 0.30f
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

    /** 注入方式：APPEND 追加；STYLE 风格语气优先、工具格式按内置；PRIORITY 全面优先；REPLACE 替换内置。 */
    enum class PromptMode { APPEND, STYLE, PRIORITY, REPLACE }


    fun promptEnabled(): Boolean = prefs?.getBoolean("custom_prompt_enabled", true) ?: true

    fun setPromptEnabled(value: Boolean) {
        prefs?.edit()?.putBoolean("custom_prompt_enabled", value)?.apply()
        bump()
    }

    fun promptMode(): PromptMode =
        runCatching { PromptMode.valueOf(prefs?.getString("custom_prompt_mode", null) ?: "STYLE") }
            .getOrDefault(PromptMode.STYLE)

    fun setPromptMode(mode: PromptMode) {
        prefs?.edit()?.putString("custom_prompt_mode", mode.name)?.apply()
        bump()
    }

    /** 当前内容与激活预设不一致时返回 true，用于显示「已修改」。 */
    fun promptModifiedFromPreset(): Boolean {
        val id = activePresetId() ?: return false
        val preset = promptPresets().firstOrNull { it.id == id } ?: return false
        return preset.content.trim() != systemPrompt().trim()
    }

    /** 按开关与注入方式合成最终系统提示词。 */
    fun composeSystemPrompt(builtin: String): String {
        val base = builtin.trim()
        val custom = systemPrompt().trim()
        if (!promptEnabled() || custom.isEmpty()) return base
        if (base.isEmpty()) return custom
        return when (promptMode()) {
            PromptMode.APPEND -> base + "\n\n## \u7528\u6237\u8865\u5145\u6307\u4ee4\n" + custom
            PromptMode.STYLE -> base + "\n\n## 用户自定义指令（风格优先）\n" +
                "以下指令在角色、风格、语气、语言和回答方式上覆盖上文要求；工具调用格式与输出协议仍以上文为准。\n\n" + custom
            PromptMode.PRIORITY -> base + "\n\n## 用户自定义指令（最高优先级）\n" +
                "以下指令与上文任何要求冲突时，一律以以下指令为准。\n\n" + custom
            PromptMode.REPLACE -> custom
        }
    }

    fun setSystemPrompt(value: String) {
        val persisted = prefs ?: return
        persisted.edit().putString(KEY_SYSTEM_PROMPT, value).apply()
        bump()
    }


    // ---------- 系统提示词预设库 ----------
    // 生效内容仍然只走 KEY_SYSTEM_PROMPT，运行时读取路径完全不变；
    // 预设只是若干候选，点哪条就把那条的内容写进生效字段。

    fun promptPresets(): List<PromptPreset> {
        val raw = prefs?.getString(KEY_PROMPT_LIBRARY, null)?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        PromptPreset(
                            id = id,
                            name = item.optString("name").ifBlank { "未命名" },
                            content = item.optString("content"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun activePresetId(): String? = prefs?.getString(KEY_PROMPT_ACTIVE, null)

    private fun writePresets(items: List<PromptPreset>) {
        val persisted = prefs ?: return
        val array = JSONArray()
        items.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("content", preset.content)
            )
        }
        persisted.edit().putString(KEY_PROMPT_LIBRARY, array.toString()).apply()
        bump()
    }

    /** 导入若干条（名称, 内容），返回实际写入条数。 */
    fun importPromptPresets(entries: List<Pair<String, String>>): Int {
        val incoming = entries.mapNotNull { (name, content) ->
            val text = content.trim()
            if (text.isEmpty()) return@mapNotNull null
            PromptPreset(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "导入的提示词" },
                content = text,
            )
        }
        if (incoming.isEmpty()) return 0
        writePresets(promptPresets() + incoming)
        return incoming.size
    }

    /**
     * 把当前编辑内容存成一条预设。
     *
     * 同一内容不重复入列：已存在内容完全相同的预设时直接复用该条
     * （必要时顺带更新名称），避免反复点「存为预设」堆出同名重复项。
     */
    fun saveCurrentAsPreset(name: String): Boolean {
        val text = systemPrompt().trim()
        if (text.isEmpty()) return false
        val label = name.trim().ifBlank { "当前提示词" }
        val existing = promptPresets()
        val same = existing.firstOrNull { it.content.trim() == text }
        if (same != null) {
            if (same.name != label) {
                writePresets(existing.map { if (it.id == same.id) it.copy(name = label) else it })
            }
            prefs?.edit()?.putString(KEY_PROMPT_ACTIVE, same.id)?.apply()
            bump()
            return true
        }
        val created = PromptPreset(
            id = UUID.randomUUID().toString(),
            name = label,
            content = text,
        )
        writePresets(existing + created)
        prefs?.edit()?.putString(KEY_PROMPT_ACTIVE, created.id)?.apply()
        bump()
        return true
    }

    /** 清掉内容完全重复的预设，保留最先出现的那条。返回删除条数。 */
    fun dedupePromptPresets(): Int {
        val items = promptPresets()
        val seen = mutableSetOf<String>()
        val kept = buildList {
            items.forEach { preset ->
                val key = preset.content.trim()
                if (seen.add(key)) add(preset)
            }
        }
        if (kept.size == items.size) return 0
        val removed = items.size - kept.size
        if (activePresetId() !in kept.map { it.id }) {
            prefs?.edit()?.remove(KEY_PROMPT_ACTIVE)?.apply()
        }
        writePresets(kept)
        return removed
    }

    /** 应用某条预设：写入生效字段并记下激活的预设。 */
    fun applyPromptPreset(id: String): Boolean {
        val preset = promptPresets().firstOrNull { it.id == id } ?: return false
        val persisted = prefs ?: return false
        persisted.edit()
            .putString(KEY_SYSTEM_PROMPT, preset.content)
            .putString(KEY_PROMPT_ACTIVE, preset.id)
            .apply()
        bump()
        return true
    }

    fun deletePromptPreset(id: String) {
        val remaining = promptPresets().filterNot { it.id == id }
        writePresets(remaining)
        if (activePresetId() == id) {
            prefs?.edit()?.remove(KEY_PROMPT_ACTIVE)?.apply()
        }
    }

    fun renamePromptPreset(id: String, name: String) {
        val renamed = name.trim()
        if (renamed.isEmpty()) return
        writePresets(
            promptPresets().map { preset ->
                if (preset.id == id) preset.copy(name = renamed) else preset
            }
        )
    }

    fun wallpaperDim(): Float =
        (prefs?.getFloat(KEY_WALLPAPER_DIM, DEFAULT_DIM) ?: DEFAULT_DIM).coerceIn(0f, MAX_DIM)

    /**
     * 实际绘制用的压暗浓度。
     *
     * 深色主题下白字压在亮壁纸上几乎不可读，因此给一个最低下限；
     * 用户在「外观 → 压暗」里的设置值在其之上继续叠加，上限沿用 MAX_DIM。
     */
    fun effectiveWallpaperDim(dark: Boolean, userDim: Float = wallpaperDim()): Float {
        val floor = if (dark) MIN_READABLE_DIM_DARK else MIN_READABLE_DIM_LIGHT
        return maxOf(floor, userDim).coerceIn(0f, MAX_DIM)
    }

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


    private var bitmapKey: String? = null
    private var bitmapValue: ImageBitmap? = null
    private val bitmapLock = Any()

    /** 解码并缓存壁纸位图（含模糊）；绘制阶段直接复用，不再重复解码。 */
    fun wallpaperBitmap(): ImageBitmap? {
        val file = wallpaperFile() ?: return null
        val key = "${file.absolutePath}#${wallpaperBlur()}"
        synchronized(bitmapLock) {
            if (key == bitmapKey) return bitmapValue
            val decoded = runCatching {
                val raw = decodeSampled(file) ?: return null
                val blur = wallpaperBlur()
                (if (blur > 0f) downUpBlur(raw, blur) else raw).asImageBitmap()
            }.getOrNull()
            bitmapKey = key
            bitmapValue = decoded
            return decoded
        }
    }

    private fun decodeSampled(file: File, targetMaxEdge: Int = 1440): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest > 0 && longest / sample > targetMaxEdge) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        )
    }

    /** 廉价模糊：降采样再放大，一次性完成。 */
    private fun downUpBlur(bitmap: Bitmap, radiusDp: Float): Bitmap {
        val factor = (1f + radiusDp / 3f).coerceIn(1f, 16f)
        val width = (bitmap.width / factor).toInt().coerceAtLeast(1)
        val height = (bitmap.height / factor).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
        return Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
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
