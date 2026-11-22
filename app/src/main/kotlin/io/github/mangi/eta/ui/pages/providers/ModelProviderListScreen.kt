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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TravelExplore
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.typeLabel
import io.github.mangi.eta.data.provider.BuiltinProviders
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.EtaPreferenceGroupItem
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiItem
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiPageDefaults
import io.github.mangi.eta.ui.components.coui.CouiPageSearch
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiPreferenceGroup
import io.github.mangi.eta.ui.components.coui.CouiSectionTitle
import io.github.mangi.eta.ui.components.coui.CouiSettingsIcon
import io.github.mangi.eta.ui.components.coui.CouiText
import io.github.mangi.eta.ui.components.coui.CouiTextKind
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.navigation.NewProviderType
import io.github.suqi8.coui.kmp.basic.RadioButton
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import io.github.mangi.eta.data.repository.ProviderHealth
import io.github.mangi.eta.data.repository.ProviderHealthRepository
import io.github.mangi.eta.data.repository.ProviderHealthState

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
    var retainedProviderToDelete by remember { mutableStateOf<ProviderSetting?>(null) }
    if (providerToDelete != null) retainedProviderToDelete = providerToDelete
    var showSortDialog by remember { mutableStateOf(false) }
    val providerHealth by ProviderHealthRepository.health.collectAsState(initial = emptyMap())

    LaunchedEffect(Unit) {
        RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance)
    }

    val displayNames = providers.associate { provider ->
        val nameRes = BuiltinProviders.defaultNameRes(provider.id, provider.name)
        provider.id to (if (nameRes != null) stringResource(nameRes) else provider.name)
    }
    val filteredProviders = remember(providers, searchQuery, displayNames) {
        val query = searchQuery.trim()
        providers.filter { provider ->
            val localizedName = displayNames[provider.id] ?: provider.name
            query.isBlank() ||
                provider.name.contains(query, ignoreCase = true) ||
                localizedName.contains(query, ignoreCase = true) ||
                provider.baseUrl.contains(query, ignoreCase = true) ||
                provider.typeLabel.contains(query, ignoreCase = true)
        }
    }

    val activeDeleteTarget = providerToDelete ?: retainedProviderToDelete
    val activeDeleteName = activeDeleteTarget?.let { target ->
        displayNames[target.id] ?: target.name
    }.orEmpty()
    val onConfirmDelete: () -> Unit = {
        scope.launch {
            activeDeleteTarget?.let { provider ->
                ProviderRepository.deleteProvider(provider.id)
                RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
            }
            providerToDelete = null
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors()
            CouiLazyScaffoldPage(
                title = stringResource(R.string.ui_model_provider_e8c7f5),
                onBack = onBack,
                search = CouiPageSearch(searchQuery, { searchQuery = it }, stringResource(R.string.ui_search_provider_74e049)),
                overlay = {
                    CouiConfirmDialog(
                        visible = providerToDelete != null,
                        title = stringResource(R.string.ui_remove_provider_9f848f),
                        message = stringResource(R.string.provider_delete_summary, activeDeleteName),
                        confirmText = stringResource(R.string.ui_delete_3755f5),
                        destructive = true,
                        onConfirm = onConfirmDelete,
                        onDismissRequest = { providerToDelete = null },
                        onDismissFinished = { if (providerToDelete == null) retainedProviderToDelete = null },
                    )
                },
            ) { searching ->
                if (!searching) item(key = "new_providers") {
                    CouiPreferenceGroup(title = stringResource(R.string.ui_add_new_provider_74df54)) {
                        item {
                            CouiPreference(
                                title = stringResource(R.string.catalog_i18n_title),
                                summary = stringResource(R.string.catalog_i18n_entry_summary),
                                icon = {
                                    CouiSettingsIcon(
                                        icon = Icons.Rounded.TravelExplore,
                                        tint = colors.accent,
                                    )
                                },
                                onClick = { onNavigate(AppRoute.CommunityCatalog) },
                            )
                        }
                        item {
                            CouiPreference(
                                title = stringResource(R.string.ui_added_openai_compatible_6bd471),
                                summary = stringResource(R.string.ui_support_chatgpt_deepseek_kimi_glm_qwen_etc_b31d02),
                                icon = { ProviderBrandIcon(ProviderSourceTypes.OPENAI) },
                                onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.OpenAiCompatible)) },
                            )
                        }
                        item {
                            CouiPreference(
                                title = stringResource(R.string.ui_new_anthropic_db6098),
                                summary = stringResource(R.string.ui_support_anthropic_claude_official_or_compatible_api_de3f80),
                                icon = { ProviderBrandIcon(ProviderSourceTypes.ANTHROPIC) },
                                onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Anthropic)) },
                            )
                        }
                        item {
                            CouiPreference(
                                title = stringResource(R.string.provider_i18n_add_gemini),
                                summary = stringResource(R.string.provider_i18n_add_gemini_summary),
                                icon = { ProviderBrandIcon(ProviderSourceTypes.GEMINI) },
                                onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Gemini)) },
                            )
                        }
                    }
                }
                item(key = "reorder_entry") {
                    CouiPreference(
                        title = "\u6392\u5e8f\u65b9\u5f0f",
                        summary = "\u81ea\u52a8\u6309\u540d\u79f0\u6216\u521b\u5efa\u65f6\u95f4\u6392\u5217\uff0c\u6216\u7528\u5217\u8868\u9879\u4e0a\u7684\u7bad\u5934\u624b\u52a8\u5fae\u8c03",
                        icon = { ProviderBrandIcon(ProviderSourceTypes.OPENAI) },
                        onClick = { showSortDialog = true },
                    )
                }
                item(key = "health_test_entry") {
                    CouiPreference(
                        title = "\u68c0\u6d4b\u5168\u90e8\u4f9b\u5e94\u5546",
                        summary = providerHealthSummary(providerHealth),
                        icon = { ProviderBrandIcon(ProviderSourceTypes.OPENAI) },
                        onClick = { ProviderHealthRepository.testAll(providers) },
                    )
                }
                item(key = "configured_providers") {
                    CouiSectionTitle(
                        pluralStringResource(R.plurals.provider_configured_count, filteredProviders.size, filteredProviders.size),
                    )
                }
                if (filteredProviders.isEmpty()) {
                    item(key = "empty_providers") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CouiText(
                                text = stringResource(
                                    if (searching) R.string.provider_no_matches else R.string.provider_empty,
                                ),
                                kind = CouiTextKind.ItemSummary,
                                color = colors.summary,
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredProviders, key = { _, provider -> provider.id }) { index, provider ->
                        EtaPreferenceGroupItem(
                            isFirst = index == 0,
                            isLast = index == filteredProviders.lastIndex,
                            hasLeadingIcon = true,
                        ) {
                            ProviderListItem(
                                provider = provider,
                                displayName = displayNames[provider.id] ?: provider.name,
                                isSelected = provider.id == selectedProviderId,
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
                                health = providerHealth[provider.id],
                                canMoveUp = index > 0,
                                canMoveDown = index < filteredProviders.lastIndex,
                                onMoveUp = { scope.launch { ProviderRepository.moveProvider(provider.id, -1) } },
                                onMoveDown = { scope.launch { ProviderRepository.moveProvider(provider.id, 1) } },
                            )
                        }
                    }
                }
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
                            title = stringResource(R.string.catalog_i18n_title),
                            summary = stringResource(R.string.catalog_i18n_entry_summary),
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
                            title = stringResource(R.string.provider_i18n_add_gemini),
                            summary = stringResource(R.string.provider_i18n_add_gemini_summary),
                            startAction = { ProviderBrandIcon(ProviderSourceTypes.GEMINI) },
                            onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.Gemini)) },
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
                            filteredProviders.forEach { provider ->
                                ProviderListItem(
                                    provider = provider,
                                    displayName = displayNames[provider.id] ?: provider.name,
                                    isSelected = provider.id == selectedProviderId,
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

    if (LocalUiStyle.current == AppearanceUiStyle.MIUIX) {
        GlassDialog(
            show = providerToDelete != null,
            backdrop = currentGlassOverlayBackdrop(),
            title = stringResource(R.string.ui_remove_provider_9f848f),
            summary = stringResource(R.string.provider_delete_summary, activeDeleteName),
            onDismissRequest = { providerToDelete = null },
            onDismissFinished = { if (providerToDelete == null) retainedProviderToDelete = null },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderListItem(
    provider: ProviderSetting,
    displayName: String,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    onSelect: () -> Unit,
    health: ProviderHealth? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val opacity = if (provider.isEnabled) 1f else 0.6f
    val healthLabel = providerHealthLabel(health)
    val subtitle = listOfNotNull(
        provider.typeLabel,
        pluralStringResource(R.plurals.provider_models_count, provider.models.size, provider.models.size),
        stringResource(R.string.ui_built_in_09ceea).takeIf { provider.isBuiltIn },
        stringResource(R.string.ui_disabled_0fe5a9).takeIf { !provider.isEnabled },
        healthLabel?.first,
    ).joinToString(" · ")

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val colors = CouiPageDefaults.themeColors()
            CouiItem(
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
                            text = displayName,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderReorderArrows(
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            onMoveUp = onMoveUp,
                            onMoveDown = onMoveDown,
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
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
                                onClick = null,
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
                        text = displayName,
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

/** 列表项健康状态：测试中/可用/失败，未测返回 null。 */
private fun providerHealthLabel(health: ProviderHealth?): Pair<String, Boolean>? {
    return when (health?.state) {
        ProviderHealthState.Testing -> "\u68c0\u6d4b\u4e2d\u2026" to true
        ProviderHealthState.Reachable -> "\u5bf9\u8bdd\u53ef\u7528 \u00b7 ${health.modelCount} \u4e2a\u6a21\u578b" to true
        ProviderHealthState.Unreachable -> "\u8fde\u63a5\u5931\u8d25\uff1a${health.detail.orEmpty()}" to false
        else -> null
    }
}

/** 顶部入口摘要：未测 / 进行中 / 结果统计。 */
private fun providerHealthSummary(health: Map<String, ProviderHealth>): String {
    if (health.isEmpty()) return "\u5bf9\u6bcf\u4e2a\u5df2\u542f\u7528\u4f9b\u5e94\u5546\u53d1\u4e00\u6761\u6781\u5c0f\u6d88\u606f\u5b9e\u6d4b\u5bf9\u8bdd\uff0c\u7ed3\u679c\u4ee3\u8868\u771f\u5b9e\u53ef\u7528\u6027"
    if (health.values.any { it.state == ProviderHealthState.Testing }) return "\u68c0\u6d4b\u4e2d\u2026"
    val reachable = health.values.count { it.state == ProviderHealthState.Reachable }
    val failed = health.values.count { it.state == ProviderHealthState.Unreachable }
    return "\u53ef\u7528 $reachable \u4e2a \u00b7 \u8fde\u63a5\u5931\u8d25 $failed \u4e2a"
}

/** 手动微调上下箭头；到顶/到底时对应箭头变灰。 */
@Composable
private fun ProviderReorderArrows(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val colors = if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        CouiPageDefaults.themeColors()
    } else {
        null
    }
    val activeTint = colors?.accent ?: MiuixTheme.colorScheme.primary
    val idleTint = (colors?.summary ?: MiuixTheme.colorScheme.onSurfaceVariantSummary).copy(alpha = 0.35f)
    Column {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = "\u4e0a\u79fb",
            tint = if (canMoveUp) activeTint else idleTint,
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = canMoveUp) { onMoveUp?.invoke() },
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "\u4e0b\u79fb",
            tint = if (canMoveDown) activeTint else idleTint,
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = canMoveDown) { onMoveDown?.invoke() },
        )
    }
}
