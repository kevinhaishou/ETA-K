package io.github.mangi.eta.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import kotlin.math.roundToInt
import kotlin.math.abs
import io.github.mangi.eta.customization.WallpaperScale
import io.github.mangi.eta.customization.CustomizationStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreferenceGroup
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val appearance = LocalAppearanceSettings.current
    val coroutineScope = rememberCoroutineScope()

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        coroutineScope.launch {
            AppearanceSettingsRepository.update(transform)
        }
    }

    fun selectUiStyle(uiStyle: AppearanceUiStyle) {
        coroutineScope.launch {
            AppearanceSettingsRepository.selectUiStyle(uiStyle)
        }
    }

    val context = LocalContext.current
    val wallpaperRevision by CustomizationStore.revision.collectAsState()
    val hasWallpaper = remember(wallpaperRevision) { CustomizationStore.hasWallpaper() }
    val wallpaperEnabled = remember(wallpaperRevision) { CustomizationStore.wallpaperEnabled() }
    var blurDraft by remember(wallpaperRevision) { mutableFloatStateOf(CustomizationStore.wallpaperBlur()) }
    var dimDraft by remember(wallpaperRevision) {
        mutableFloatStateOf(CustomizationStore.wallpaperDim() * 100f)
    }
    var sidebarDraft by remember(wallpaperRevision) {
        mutableFloatStateOf(CustomizationStore.sidebarAlpha() * 100f)
    }
    val wallpaperScale = remember(wallpaperRevision) { CustomizationStore.wallpaperScale() }
    val wallpaperScaleLabels = listOf(
        stringResource(R.string.appearance_wallpaper_scale_fill),
        stringResource(R.string.appearance_wallpaper_scale_fit),
    )
    val dimSteps = listOf(0f, 15f, 30f, 45f, 60f, 75f, 85f)
    val dimLabels = dimSteps.map { "${it.roundToInt()}%" }
    val dimIndex = dimSteps.indices.minByOrNull { abs(dimSteps[it] - dimDraft) } ?: 0
    val blurSteps = listOf(0f, 4f, 8f, 12f, 16f, 24f)
    val blurLabels = blurSteps.map { "${it.roundToInt()}dp" }
    val blurIndex = blurSteps.indices.minByOrNull { abs(blurSteps[it] - blurDraft) } ?: 0
    val sidebarSteps = listOf(0f, 10f, 20f, 30f, 40f)
    val sidebarLabels = sidebarSteps.map { "${it.roundToInt()}%" }
    val sidebarIndex = sidebarSteps.indices.minByOrNull { abs(sidebarSteps[it] - sidebarDraft) } ?: 0
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) CustomizationStore.importWallpaper(context, uri)
    }

    fun pickWallpaper() {
        wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun commitWallpaperDim() {
        CustomizationStore.setWallpaperDim(dimDraft / 100f)
    }

    val uiStyles = remember { AppearanceUiStyle.entries.sortedBy { it != AppearanceUiStyle.Default } }
    val uiStyleLabels = uiStyles.map { style ->
        when (style) {
            AppearanceUiStyle.MIUIX -> stringResource(R.string.appearance_ui_style_miuix)
            AppearanceUiStyle.COUI -> stringResource(R.string.appearance_ui_style_coui)
        }
    }
    val themeModes = AppearanceThemeMode.entries
    val themeModeLabels = listOf(
        stringResource(R.string.appearance_theme_system),
        stringResource(R.string.appearance_theme_light),
        stringResource(R.string.appearance_theme_dark),
    )
    val paletteStyles = AppearancePaletteStyle.entries
    val paletteLabels = listOf(
        stringResource(R.string.appearance_palette_tonal_spot),
        stringResource(R.string.appearance_palette_neutral),
        stringResource(R.string.appearance_palette_vibrant),
        stringResource(R.string.appearance_palette_expressive),
        stringResource(R.string.appearance_palette_rainbow),
        stringResource(R.string.appearance_palette_fruit_salad),
        stringResource(R.string.appearance_palette_monochrome),
        stringResource(R.string.appearance_palette_fidelity),
        stringResource(R.string.appearance_palette_content),
        stringResource(R.string.appearance_palette_cmf),
    )
    val accentColors = AppearanceAccentColor.entries
    val accentLabels = listOf(
        stringResource(R.string.appearance_accent_system),
        stringResource(R.string.appearance_accent_blue),
        stringResource(R.string.appearance_accent_purple),
        stringResource(R.string.appearance_accent_pink),
        stringResource(R.string.appearance_accent_red),
        stringResource(R.string.appearance_accent_orange),
        stringResource(R.string.appearance_accent_yellow),
        stringResource(R.string.appearance_accent_green),
        stringResource(R.string.appearance_accent_teal),
    )

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.appearance_title),
                onBack = onBack,
            ) {
                CouiPreferenceGroup(title = stringResource(R.string.appearance_group_color)) {
                    item {
                        CouiDropdownPreference(
                            title = stringResource(R.string.appearance_ui_style),
                            items = uiStyleLabels,
                            selectedIndex = uiStyles.indexOf(appearance.uiStyle),
                            onSelectedIndexChange = { index ->
                                uiStyles.getOrNull(index)?.let(::selectUiStyle)
                            },
                        )
                    }
                    item {
                        CouiDropdownPreference(
                            title = stringResource(R.string.appearance_theme_mode),
                            items = themeModeLabels,
                            selectedIndex = appearance.themeMode.ordinal,
                            onSelectedIndexChange = { index ->
                                themeModes.getOrNull(index)?.let { mode ->
                                    update { current -> current.copy(themeMode = mode) }
                                }
                            },
                        )
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.appearance_group_interface)) {
                    item {
                        CouiSwitchPreference(
                            title = stringResource(R.string.appearance_swipe_dismiss),
                            summary = stringResource(R.string.appearance_swipe_dismiss_summary),
                            checked = appearance.swipeDismissEnabled,
                            onCheckedChange = { enabled ->
                                update { current -> current.copy(swipeDismissEnabled = enabled) }
                            },
                        )
                    }
                    item {
                        CouiSwitchPreference(
                            title = stringResource(R.string.appearance_predictive_back),
                            summary = stringResource(R.string.appearance_predictive_back_summary),
                            checked = appearance.predictiveBackEnabled,
                            onCheckedChange = { enabled ->
                                update { current -> current.copy(predictiveBackEnabled = enabled) }
                            },
                        )
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.appearance_chat_background_group)) {
                    item {
                        CouiSwitchPreference(
                            title = stringResource(R.string.appearance_wallpaper_enabled),
                            summary = stringResource(R.string.appearance_wallpaper_enabled_summary),
                            checked = wallpaperEnabled,
                            onCheckedChange = { CustomizationStore.setWallpaperEnabled(it) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.appearance_chat_background_pick),
                            summary = stringResource(R.string.appearance_chat_background_summary),
                            onClick = { pickWallpaper() },
                        )
                    }
                    item {
                        CouiDropdownPreference(
                            title = stringResource(R.string.appearance_chat_background_dim),
                            items = dimLabels,
                            selectedIndex = dimIndex,
                            onSelectedIndexChange = { index ->
                                dimSteps.getOrNull(index)?.let { dimDraft = it }
                                commitWallpaperDim()
                            },
                        )
                    }
                    item {
                        CouiDropdownPreference(
                            title = stringResource(R.string.appearance_wallpaper_blur),
                            items = blurLabels,
                            selectedIndex = blurIndex,
                            onSelectedIndexChange = { index ->
                                blurSteps.getOrNull(index)?.let { value ->
                                    blurDraft = value
                                    CustomizationStore.setWallpaperBlur(value)
                                }
                            },
                        )
                    }
                    item {
                        CouiDropdownPreference(
                            title = stringResource(R.string.appearance_sidebar_alpha),
                            items = sidebarLabels,
                            selectedIndex = sidebarIndex,
                            onSelectedIndexChange = { index ->
                                sidebarSteps.getOrNull(index)?.let { value ->
                                    sidebarDraft = value
                                    CustomizationStore.setSidebarAlpha(value / 100f)
                                }
                            },
                        )
                    }
                    item {
                        CouiDropdownPreference(
                            title = stringResource(R.string.appearance_wallpaper_scale),
                            items = wallpaperScaleLabels,
                            selectedIndex = wallpaperScale.ordinal,
                            onSelectedIndexChange = { index ->
                                WallpaperScale.entries.getOrNull(index)?.let {
                                    CustomizationStore.setWallpaperScale(it)
                                }
                            },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.appearance_chat_background_clear),
                            enabled = hasWallpaper,
                            onClick = { CustomizationStore.clearWallpaper(context) },
                        )
                    }
                }

                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.appearance_title),
                onBack = onBack,
            ) {
                item(key = "appearance_color") {
                    val backdrop = currentGlassOverlayBackdrop()
                    SmallTitle(text = stringResource(R.string.appearance_group_color))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        GlassDropdownPreference(
                            title = stringResource(R.string.appearance_ui_style),
                            items = uiStyleLabels,
                            selectedIndex = uiStyles.indexOf(appearance.uiStyle),
                            backdrop = backdrop,
                            onSelectedIndexChange = { index ->
                                uiStyles.getOrNull(index)?.let(::selectUiStyle)
                            },
                        )
                        GlassDropdownPreference(
                            title = stringResource(R.string.appearance_theme_mode),
                            items = themeModeLabels,
                            selectedIndex = appearance.themeMode.ordinal,
                            backdrop = backdrop,
                            onSelectedIndexChange = { index ->
                                themeModes.getOrNull(index)?.let { mode ->
                                    update { current -> current.copy(themeMode = mode) }
                                }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.appearance_monet),
                            summary = stringResource(R.string.appearance_monet_summary),
                            checked = appearance.monetEnabled,
                            onCheckedChange = { enabled ->
                                update { current -> current.copy(monetEnabled = enabled) }
                            },
                        )
                        AnimatedVisibility(
                            visible = appearance.monetEnabled,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            Column {
                                GlassDropdownPreference(
                                    title = stringResource(R.string.appearance_palette_style),
                                    items = paletteLabels,
                                    selectedIndex = appearance.paletteStyle.ordinal,
                                    backdrop = backdrop,
                                    onSelectedIndexChange = { index ->
                                        paletteStyles.getOrNull(index)?.let { style ->
                                            update { current -> current.copy(paletteStyle = style) }
                                        }
                                    },
                                )
                                GlassDropdownPreference(
                                    title = stringResource(R.string.appearance_accent_color),
                                    items = accentLabels,
                                    selectedIndex = appearance.accentColor.ordinal,
                                    backdrop = backdrop,
                                    onSelectedIndexChange = { index ->
                                        accentColors.getOrNull(index)?.let { accent ->
                                            update { current -> current.copy(accentColor = accent) }
                                        }
                                    },
                                )
                                SwitchPreference(
                                    title = stringResource(R.string.appearance_pure_black),
                                    summary = stringResource(R.string.appearance_pure_black_summary),
                                    checked = appearance.pureBlackEnabled,
                                    onCheckedChange = { enabled ->
                                        update { current -> current.copy(pureBlackEnabled = enabled) }
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "appearance_interface") {
                    SmallTitle(text = stringResource(R.string.appearance_group_interface))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.appearance_swipe_dismiss),
                            summary = stringResource(R.string.appearance_swipe_dismiss_summary),
                            checked = appearance.swipeDismissEnabled,
                            onCheckedChange = { enabled ->
                                update { current -> current.copy(swipeDismissEnabled = enabled) }
                            },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.appearance_predictive_back),
                            summary = stringResource(R.string.appearance_predictive_back_summary),
                            checked = appearance.predictiveBackEnabled,
                            onCheckedChange = { enabled ->
                                update { current -> current.copy(predictiveBackEnabled = enabled) }
                            },
                        )
                        if (isRuntimeShaderSupported()) {
                            SwitchPreference(
                                title = stringResource(R.string.appearance_progressive_blur),
                                summary = stringResource(R.string.appearance_progressive_blur_summary),
                                checked = appearance.progressiveBlurEnabled,
                                onCheckedChange = { enabled ->
                                    update { current -> current.copy(progressiveBlurEnabled = enabled) }
                                },
                            )
                        }
                    }
                }
                item(key = "appearance_chat_background") {
                    val backdrop = currentGlassOverlayBackdrop()
                    SmallTitle(text = stringResource(R.string.appearance_chat_background_group))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.appearance_wallpaper_enabled),
                            summary = stringResource(R.string.appearance_wallpaper_enabled_summary),
                            checked = wallpaperEnabled,
                            onCheckedChange = { CustomizationStore.setWallpaperEnabled(it) },
                        )
                        BasicComponent(
                            title = stringResource(R.string.appearance_chat_background_pick),
                            summary = stringResource(R.string.appearance_chat_background_summary),
                            onClick = { pickWallpaper() },
                        )
                        GlassDropdownPreference(
                            title = stringResource(R.string.appearance_chat_background_dim),
                            items = dimLabels,
                            selectedIndex = dimIndex,
                            backdrop = backdrop,
                            onSelectedIndexChange = { index ->
                                dimSteps.getOrNull(index)?.let { dimDraft = it }
                                commitWallpaperDim()
                            },
                        )
                        GlassDropdownPreference(
                            title = stringResource(R.string.appearance_wallpaper_blur),
                            items = blurLabels,
                            selectedIndex = blurIndex,
                            backdrop = backdrop,
                            onSelectedIndexChange = { index ->
                                blurSteps.getOrNull(index)?.let { value ->
                                    blurDraft = value
                                    CustomizationStore.setWallpaperBlur(value)
                                }
                            },
                        )
                        GlassDropdownPreference(
                            title = stringResource(R.string.appearance_sidebar_alpha),
                            items = sidebarLabels,
                            selectedIndex = sidebarIndex,
                            backdrop = backdrop,
                            onSelectedIndexChange = { index ->
                                sidebarSteps.getOrNull(index)?.let { value ->
                                    sidebarDraft = value
                                    CustomizationStore.setSidebarAlpha(value / 100f)
                                }
                            },
                        )
                        GlassDropdownPreference(
                            title = stringResource(R.string.appearance_wallpaper_scale),
                            items = wallpaperScaleLabels,
                            selectedIndex = wallpaperScale.ordinal,
                            backdrop = backdrop,
                            onSelectedIndexChange = { index ->
                                WallpaperScale.entries.getOrNull(index)?.let {
                                    CustomizationStore.setWallpaperScale(it)
                                }
                            },
                        )
                        BasicComponent(
                            title = stringResource(R.string.appearance_chat_background_clear),
                            onClick = { CustomizationStore.clearWallpaper(context) },
                        )
                    }
                }
            }
        }
    }
}
