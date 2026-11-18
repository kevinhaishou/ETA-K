package io.github.mangi.eta.ui.pages.providers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.radiobutton.RadioButton
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.typeLabel
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.components.coui.CouiSettingsIcon
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.navigation.NewProviderType
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ModelProviderListScreen(
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val selectedProviderId by RuntimeConfigRepository.selectedProviderIdFlow().collectAsState(initial = null)
    var searchQuery by remember { mutableStateOf("") }
    var providerToDelete by remember { mutableStateOf<ProviderSetting?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    // 排序只在未过滤时开放：过滤视图中相邻交换会打乱全局语义。
    val reorderEnabled = searchQuery.isBlank()

    LaunchedEffect(Unit) {
        RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance)
    }

    val filteredProviders = remember(providers, searchQuery) {
        val query = searchQuery.trim()
        providers.filter { provider ->
            query.isBlank() ||
                provider.name.contains(query, ignoreCase = true) ||
                provider.baseUrl.contains(query, ignoreCase = true) ||
                provider.typeLabel.contains(query, ignoreCase = true)
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors()
            CouiScaffoldPage(title = stringResource(R.string.ui_model_provider_e8c7f5), onBack = onBack) {
                CouiSearchBarBox(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.ui_search_provider_74e049),
                )

                ProviderSection(title = stringResource(R.string.ui_add_new_provider_74df54)) {
                    CouiPreference(
                        title = "从 models.dev 添加",
                        summary = "浏览 models.dev 社区目录并选择模型",
                        icon = {
                            CouiSettingsIcon(
                                icon = Icons.Rounded.TravelExplore,
                                tint = colors.accent,
                            )
                        },
                        onClick = { onNavigate(AppRoute.CommunityCatalog) },
                    )
                    CouiIconDivider()
                    CouiPreference(
                        title = stringResource(R.string.ui_added_openai_compatible_6bd471),
                        summary = stringResource(R.string.ui_support_chatgpt_deepseek_kimi_glm_qwen_etc_b31d02),
                        icon = { ProviderBrandIcon(ProviderSourceTypes.OPENAI) },
                        onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.OpenAiCompatible)) },
                    )
                    CouiIconDivider()
                    CouiPreference(
                        title = stringResource(R.string.ui_new_anthropic_db6098),
                        summary = stringResource(R.string.ui_support_anthropic_claude_official_or_compatible_api_de3f80),
                        icon = { ProviderBrandIcon(ProviderSourceTypes.ANTHROPIC) },
                        onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Anthropic)) },
                    )
                    CouiIconDivider()
                    CouiPreference(
                        title = "新增 Gemini",
                        summary = "使用 API Key 连接原生 Interactions API",
                        icon = { ProviderBrandIcon(ProviderSourceTypes.GEMINI) },
                        onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Gemini)) },
                    )
                }

                ProviderSection(title = null) {
                    CouiPreference(
                        title = "排序方式",
                        summary = "自动按名称或创建时间排列，或在列表项上用箭头手动微调",
                        icon = {
                            CouiSettingsIcon(
                                icon = Icons.Rounded.KeyboardArrowUp,
                                tint = colors.accent,
                            )
                        },
                        onClick = { showSortDialog = true },
                    )
                }

                ProviderSection(
                    title = pluralStringResource(
                        R.plurals.provider_configured_count,
                        filteredProviders.size,
                        filteredProviders.size,
                    ),
                ) {
                    if (filteredProviders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiText(
                                text = if (searchQuery.isBlank()) {
                                    stringResource(R.string.provider_empty)
                                } else {
                                    stringResource(R.string.provider_no_matches)
                                },
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        }
                    } else {
                        filteredProviders.forEachIndexed { index, provider ->
                            val canMoveUp = reorderEnabled && index > 0
                            val canMoveDown = reorderEnabled && index < filteredProviders.lastIndex
                            ProviderListItem(
                                provider = provider,
                                isSelected = provider.id == selectedProviderId,
                                canMoveUp = canMoveUp,
                                canMoveDown = canMoveDown,
                                onMoveUp = if (canMoveUp) {
                                    { scope.launch { ProviderRepository.moveProvider(provider.id, -1) } }
                                } else {
                                    null
                                },
                                onMoveDown = if (canMoveDown) {
                                    { scope.launch { ProviderRepository.moveProvider(provider.id, 1) } }
                                } else {
                                    null
                                },
                                onOpen = { onNavigate(AppRoute.ModelProviderDetail(provider.id)) },
                                onDelete = if (!provider.isBuiltIn) {
                                    { providerToDelete = provider }
                                } else {
                                    null
                                },
                                onSelect = {
                                    scope.launch {
                                        RuntimeConfigRepository.setSelectedProviderId(provider.id)
                                        RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                                    }
                                },
                            )
                            if (index < filteredProviders.lastIndex) {
                                CouiIconDivider()
                            }
                        }
                    }
                }

                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(title = stringResource(R.string.ui_model_provider_e8c7f5), onBack = onBack) {
                item(key = "search") {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        label = stringResource(R.string.ui_search_provider_74e049),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                item(key = "create_section") {
                    ProviderSection(title = stringResource(R.string.ui_add_new_provider_74df54)) {
                        ArrowPreference(
                            title = "从 models.dev 添加",
                            summary = "浏览 models.dev 社区目录并选择模型",
                            startAction = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = { onNavigate(AppRoute.CommunityCatalog) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_added_openai_compatible_6bd471),
                            summary = stringResource(R.string.ui_support_chatgpt_deepseek_kimi_glm_qwen_etc_b31d02),
                            startAction = {
                                ProviderBrandIcon(ProviderSourceTypes.OPENAI)
                            },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.OpenAiCompatible)) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_new_anthropic_db6098),
                            summary = stringResource(R.string.ui_support_anthropic_claude_official_or_compatible_api_de3f80),
                            startAction = {
                                ProviderBrandIcon(ProviderSourceTypes.ANTHROPIC)
                            },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Anthropic)) },
                        )
                        ArrowPreference(
                            title = "新增 Gemini",
                            summary = "使用 API Key 连接原生 Interactions API",
                            startAction = { ProviderBrandIcon(ProviderSourceTypes.GEMINI) },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Gemini)) },
                        )
                    }
                }

                item(key = "sort_entry") {
                    ProviderSection(title = null) {
                        ArrowPreference(
                            title = "排序方式",
                            summary = "自动按名称或创建时间排列，或在列表项上用箭头手动微调",
                            onClick = { showSortDialog = true },
                        )
                    }
                }

                item(key = "list_section") {
                    ProviderSection(title = pluralStringResource(R.plurals.provider_configured_count, filteredProviders.size, filteredProviders.size)) {
                        if (filteredProviders.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) {
                                        stringResource(R.string.provider_empty)
                                    } else {
                                        stringResource(R.string.provider_no_matches)
                                    },
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        } else {
                            filteredProviders.forEachIndexed { index, provider ->
                                val canMoveUp = reorderEnabled && index > 0
                                val canMoveDown = reorderEnabled && index < filteredProviders.lastIndex
                                ProviderListItem(
                                    provider = provider,
                                    isSelected = provider.id == selectedProviderId,
                                    canMoveUp = canMoveUp,
                                    canMoveDown = canMoveDown,
                                    onMoveUp = if (canMoveUp) {
                                        { scope.launch { ProviderRepository.moveProvider(provider.id, -1) } }
                                    } else {
                                        null
                                    },
                                    onMoveDown = if (canMoveDown) {
                                        { scope.launch { ProviderRepository.moveProvider(provider.id, 1) } }
                                    } else {
                                        null
                                    },
                                    onOpen = { onNavigate(AppRoute.ModelProviderDetail(provider.id)) },
                                    onDelete = if (!provider.isBuiltIn) {
                                        { providerToDelete = provider }
                                    } else {
                                        null
                                    },
                                    onSelect = {
                                        scope.launch {
                                            RuntimeConfigRepository.setSelectedProviderId(provider.id)
                                            RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        val modes = listOf(
            "按名称 A → Z" to ProviderRepository.ProviderSortMode.NAME_ASC,
            "按名称 Z → A" to ProviderRepository.ProviderSortMode.NAME_DESC,
            "按创建时间（新 → 旧）" to ProviderRepository.ProviderSortMode.CREATED_DESC,
        )
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiConfirmDialog(
                    visible = true,
                    title = "排序方式",
                    message = "选择自动排序规则，立即应用到列表顺序",
                    confirmText = "完成",
                    onConfirm = { showSortDialog = false },
                    onDismissRequest = { showSortDialog = false },
                ) {
                    modes.forEach { (label, mode) ->
                        CouiPreference(
                            title = label,
                            onClick = {
                                scope.launch { ProviderRepository.autoSortProviders(mode) }
                                showSortDialog = false
                            },
                        )
                    }
                }
            }
            AppearanceUiStyle.MIUIX -> {
                OverlayDialog(
                    show = true,
                    title = "排序方式",
                    summary = "选择自动排序规则，立即应用到列表顺序",
                    onDismissRequest = { showSortDialog = false },
                ) {
                    modes.forEach { (label, mode) ->
                        BasicComponent(
                            title = label,
                            onClick = {
                                scope.launch { ProviderRepository.autoSortProviders(mode) }
                                showSortDialog = false
                            },
                        )
                    }
                    MiuixDialogActions(
                        confirmText = "完成",
                        onCancel = { showSortDialog = false },
                        onConfirm = { showSortDialog = false },
                    )
                }
            }
        }
    }

    if (providerToDelete != null) {
        val target = providerToDelete
        val onConfirmDelete: () -> Unit = {
            scope.launch {
                target?.let { provider ->
                    ProviderRepository.deleteProvider(provider.id)
                    RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                }
                providerToDelete = null
            }
        }
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiConfirmDialog(
                    visible = true,
                    title = stringResource(R.string.ui_remove_provider_9f848f),
                    message = stringResource(R.string.provider_delete_summary, target?.name.orEmpty()),
                    confirmText = stringResource(R.string.ui_delete_3755f5),
                    destructive = true,
                    onConfirm = onConfirmDelete,
                    onDismissRequest = { providerToDelete = null },
                )
            }
            AppearanceUiStyle.MIUIX -> {
                OverlayDialog(
                    show = true,
                    title = stringResource(R.string.ui_remove_provider_9f848f),
                    summary = stringResource(R.string.provider_delete_summary, target?.name.orEmpty()),
                    onDismissRequest = { providerToDelete = null },
                ) {
                    MiuixDialogActions(
                        confirmText = stringResource(R.string.ui_delete_3755f5),
                        destructive = true,
                        onCancel = { providerToDelete = null },
                        onConfirm = onConfirmDelete,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderListItem(
    provider: ProviderSetting,
    isSelected: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    // 竖排上下箭头：与选择控件并排，紧凑且不破坏既有布局。
    @Composable
    fun MoveArrows(titleColor: Color) {
        Column(
            modifier = Modifier.width(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp, 16.dp)
                    .clickable(enabled = canMoveUp, onClick = { onMoveUp?.invoke() }, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "上移",
                    modifier = Modifier.size(18.dp),
                    tint = titleColor.copy(alpha = if (canMoveUp) 1f else 0.3f),
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp, 16.dp)
                    .clickable(enabled = canMoveDown, onClick = { onMoveDown?.invoke() }, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "下移",
                    modifier = Modifier.size(18.dp),
                    tint = titleColor.copy(alpha = if (canMoveDown) 1f else 0.3f),
                )
            }
        }
    }

    val opacity = if (provider.isEnabled) 1f else 0.6f
    val subtitle = listOfNotNull(
        provider.typeLabel,
        pluralStringResource(R.plurals.provider_models_count, provider.models.size, provider.models.size),
        stringResource(R.string.ui_built_in_09ceea).takeIf { provider.isBuiltIn },
        stringResource(R.string.ui_disabled_0fe5a9).takeIf { !provider.isEnabled },
    ).joinToString(" · ")

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors()
            Item(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = onDelete)
                    .graphicsLayer { alpha = opacity },
                icon = { ProviderIcon(provider) },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CouiText(
                            text = provider.name,
                            kind = CouiTextKind.ItemTitle,
                            color = if (isSelected) colors.accent else colors.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isSelected) {
                            TagChip(text = stringResource(R.string.ui_current_25e74d), tone = TagChipTone.Emphasized)
                        }
                    }
                },
                summary = {
                    if (provider.baseUrl.isNotBlank()) {
                        CouiText(
                            text = provider.baseUrl,
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CouiText(
                        text = subtitle,
                        kind = CouiTextKind.GroupFooter,
                        color = colors.summary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (onMoveUp != null || onMoveDown != null) {
                            MoveArrows(colors.title)
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    enabled = provider.isEnabled,
                                    role = Role.RadioButton,
                                    onClick = onSelect,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            RadioButton(
                                modifier = Modifier.size(24.dp),
                                selected = isSelected,
                                enabled = provider.isEnabled,
                            )
                        }
                    }
                },
            )
        }
        AppearanceUiStyle.MIUIX -> {
            BasicComponent(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = onDelete)
                    .graphicsLayer { alpha = opacity },
                startAction = { ProviderIcon(provider) },
                endActions = {
                    if (onMoveUp != null || onMoveDown != null) {
                        MoveArrows(MiuixTheme.colorScheme.onSurface)
                    }
                    Checkbox(
                        state = ToggleableState(isSelected),
                        onClick = if (provider.isEnabled) onSelect else null,
                        enabled = provider.isEnabled,
                    )
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = provider.name,
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isSelected) {
                        TagChip(text = stringResource(R.string.ui_current_25e74d), tone = TagChipTone.Emphasized)
                    }
                }
                Text(
                    text = provider.baseUrl,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
