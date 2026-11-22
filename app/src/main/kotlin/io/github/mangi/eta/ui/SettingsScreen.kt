package io.github.mangi.eta.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImportContacts
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.JoinLeft
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog
import io.github.mangi.eta.agent.device.NotificationTriggerStore
import io.github.mangi.eta.agent.device.NotificationTriggerRule
import io.github.mangi.eta.agent.device.NotificationTriggerLogStore
import io.github.mangi.eta.agent.device.NotificationTriggerLogEntry
import io.github.mangi.eta.agent.accessibility.AccessibilityProtectionClient
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.agent.voice.AssistantRoleHelper
import io.github.mangi.eta.agent.voice.AssistantRoleStatus
import io.github.mangi.eta.agent.voice.DefaultAssistantOwner
import io.github.mangi.eta.config.PowerAssistantTarget
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.core.ModuleConfig
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.ImageGenSettings
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.provider.BuiltinProviders
import io.github.mangi.eta.data.repository.ImageGenSettingsRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.data.update.AppLatestRelease
import io.github.mangi.eta.data.update.AppUpdateChecker
import io.github.mangi.eta.ui.app.EnhancementSettingsHistory
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.LanguagePreference
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.components.StatusWarning
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPageDefaults
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiPreferenceGroup
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSettingsIcon
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.screens.imagegen.shortName
import io.github.suqi8.coui.kmp.theme.COUITheme as CouiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private object SettingsIconColors {
    val Blue = Color(0xFF4285F4)
    val Green = StatusSuccess
    val Orange = Color(0xFFFA7B17)
    val Yellow = StatusWarning
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsIcon(
    icon: ImageVector,
    enabled: Boolean = true,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(24.dp),
        tint = if (enabled) {
            MiuixTheme.colorScheme.onBackground
        } else {
            MiuixTheme.colorScheme.disabledOnSecondaryVariant
        },
    )
}

private data class SettingsSystemStatus(
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val accessibilityProtectionEnabled: Boolean = false,
    val assistantRoleStatus: AssistantRoleStatus = AssistantRoleStatus(isDefaultAssistant = false),
)

@Volatile
private var cachedSettingsSystemStatus: SettingsSystemStatus? = null

/**
 * 模块配置界面。
 *
 * 开关默认值由 [Prefs.Keys.BOOLEAN_DEFAULTS] 统一定义。Eta Runtime 自己消费的开关写入
 * App 本地配置；仅 Hook 消费的开关通过 RemotePreferences 提交到 LSPosed。
 */
@Composable
internal fun SettingsScreen(
    context: Context,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageContent(context = context, onNavigate = onNavigate, onBack = onBack)
}

@Composable
private fun SettingsPageContent(
    context: Context,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val enhancementHistory = remember(context.applicationContext) { EnhancementSettingsHistory(context) }
    var hasConnectedFramework by remember { mutableStateOf(enhancementHistory.hasConnected) }

    // 悬浮窗与无障碍状态走异步查询，避免首帧进入设置页时在主线程触发同步 Binder IPC。
    var systemStatus by remember {
        mutableStateOf(cachedSettingsSystemStatus ?: SettingsSystemStatus())
    }
    val overlayGranted = systemStatus.overlayGranted
    val accessibilityGranted = systemStatus.accessibilityGranted
    val accessibilityProtectionEnabled = systemStatus.accessibilityProtectionEnabled
    val assistantRoleStatus = systemStatus.assistantRoleStatus
    var accessibilityProtectionPending by remember { mutableStateOf(false) }

    // 关于组：版本信息与更新检查。结果对话框在列表外渲染，状态需要页面级 owner。
    val appVersionName = BuildConfig.VERSION_NAME
    val appVersionSummary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val openUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppLatestRelease?>(null) }
    var retainedUpdate by remember { mutableStateOf<AppLatestRelease?>(null) }
    if (availableUpdate != null) retainedUpdate = availableUpdate
    val checkForUpdate: () -> Unit = {
        if (!checkingUpdate) {
            checkingUpdate = true
            coroutineScope.launch {
                val release = runCatching {
                    withContext(Dispatchers.IO) { AppUpdateChecker.fetchLatest() }
                }.getOrNull()
                checkingUpdate = false
                when {
                    release == null -> Toast.makeText(
                        context.applicationContext,
                        context.getString(R.string.ui_update_check_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                    AppUpdateChecker.isNewer(release.version, appVersionName) -> availableUpdate = release
                    else -> Toast.makeText(
                        context.applicationContext,
                        context.getString(R.string.ui_update_already_latest),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // Provider / Model 选中状态展示
    val initialProviders = remember { ProviderRepository.cachedProviders() }
    val initialProviderId = remember { RuntimeConfigRepository.cachedSelectedProviderId() }
    val initialModelId = remember { RuntimeConfigRepository.cachedSelectedModelId() }
    val providers by ProviderRepository.providersFlow().collectAsState(initial = initialProviders)
    val selectedProviderId by RuntimeConfigRepository.selectedProviderIdFlow()
        .collectAsState(initial = initialProviderId)
    val selectedModelId by RuntimeConfigRepository.selectedModelIdFlow()
        .collectAsState(initial = initialModelId)
    val selectedProvider = remember(providers, selectedProviderId) {
        providers.find { it.id == selectedProviderId }
    }
    val selectedModel = remember(selectedProvider, selectedModelId) {
        selectedProvider?.models?.find { it.id == selectedModelId }
    }
    val providerSummary = selectedProvider?.let { provider ->
        val providerDisplayName = BuiltinProviders.defaultNameRes(provider.id, provider.name)?.let { stringResource(it) } ?: provider.name
        "$providerDisplayName / ${selectedModel?.displayName ?: stringResource(R.string.settings_model_not_selected)}"
    } ?: stringResource(R.string.settings_not_configured)

    // prefs 绑定到 XposedService：service 到达时切换到 RemotePreferences（跨进程提交到
    // LSPosed 数据库）；未就绪时保持 null，UI 禁止修改。
    var prefs by remember {
        mutableStateOf(Prefs.cachedRemotePreferencesForUi(EtaApp.serviceInstance))
    }
    val agentPrefs = remember { Prefs.localAgentPreferences() }
    val pickerState = remember(providers, selectedProviderId, selectedModelId) {
        AgentModelPickerProjector.project(providers, selectedProviderId, selectedModelId)
    }
    val effectivePrimary = remember(providers, pickerState.selectedModel?.id) {
        providers.enabledModel(pickerState.selectedModel?.id)
    }
    val effectiveCapabilities = remember(effectivePrimary) {
        effectivePrimary?.let { (provider, model) ->
            RuntimeConfigRepository.reasoningCapabilities(provider, model)
        }
    }
    val effortOptions = remember(effectiveCapabilities) {
        effectiveCapabilities?.selectableEfforts
            ?.filter { it != ReasoningEffort.OFF }
            .orEmpty()
    }
    val providerDefaultLabel = stringResource(R.string.conversation_title_settings_reasoning_provider_default)
    val effortLabels = remember(effortOptions, providerDefaultLabel) {
        effortOptions.map { effort ->
            when (effort) {
                ReasoningEffort.DEFAULT -> providerDefaultLabel
                else -> effort.displayName
            }
        }
    }
    val defaultThinkingKey = Prefs.Keys.AGENT_THINKING_ENABLED
    val defaultThinkingFallback = Prefs.Keys.BOOLEAN_DEFAULTS.getValue(defaultThinkingKey)
    var defaultThinkingEnabled by remember(agentPrefs) {
        mutableStateOf(
            agentPrefs?.getBoolean(defaultThinkingKey, defaultThinkingFallback)
                ?: enhancementHistory.checked(defaultThinkingKey, defaultThinkingFallback),
        )
    }
    var savedDefaultEffort by remember(agentPrefs) {
        mutableStateOf(Prefs.defaultReasoningEffort(agentPrefs))
    }
    DisposableEffect(agentPrefs) {
        val targetPrefs = agentPrefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            when (key) {
                defaultThinkingKey -> {
                    defaultThinkingEnabled = changedPrefs.getBoolean(defaultThinkingKey, defaultThinkingFallback)
                }
                Prefs.Keys.AGENT_DEFAULT_REASONING_EFFORT -> {
                    savedDefaultEffort = Prefs.defaultReasoningEffort(changedPrefs)
                }
            }
        }
        targetPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { targetPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val effectiveSelectedEffort = remember(effectiveCapabilities, savedDefaultEffort) {
        effectiveCapabilities?.normalize(savedDefaultEffort) ?: ReasoningEffort.DEFAULT
    }
    val selectedEffortIndex = remember(effortOptions, effectiveSelectedEffort) {
        effortOptions.indexOf(effectiveSelectedEffort).takeIf { it >= 0 } ?: 0
    }
    val onDefaultEffortSelect: (Int) -> Unit = { index ->
        val chosen = effortOptions.getOrNull(index)
        val targetPrefs = agentPrefs
        if (chosen != null && targetPrefs != null) {
            if (putStringSync(targetPrefs, Prefs.Keys.AGENT_DEFAULT_REASONING_EFFORT, chosen.wireValue)) {
                savedDefaultEffort = chosen
            } else {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.settings_write_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val defaultEffortTitle = stringResource(R.string.settings_default_reasoning_effort)
    val defaultEffortUnsupported = stringResource(R.string.conversation_title_settings_reasoning_unsupported)
    val defaultEffortEnabled = agentPrefs != null && defaultThinkingEnabled && effortOptions.size > 1
    var powerAssistantTarget by remember(prefs) {
        mutableStateOf(prefs?.let(Prefs::powerAssistantTarget) ?: enhancementHistory.powerTarget())
    }
    var assistantRefreshJob by remember { mutableStateOf<Job?>(null) }
    val scheduleAssistantRoleRefresh: () -> Unit = {
        assistantRefreshJob?.cancel()
        assistantRefreshJob = coroutineScope.launch {
            val status = withContext(Dispatchers.IO) { AssistantRoleHelper.queryStatus(context) }
            val next = systemStatus.copy(assistantRoleStatus = status)
            cachedSettingsSystemStatus = next
            systemStatus = next
            delay(450L)
            val delayedStatus = withContext(Dispatchers.IO) { AssistantRoleHelper.queryStatus(context) }
            val settled = systemStatus.copy(assistantRoleStatus = delayedStatus)
            cachedSettingsSystemStatus = settled
            systemStatus = settled
        }
    }
    LaunchedEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleAssistantRoleRefresh()
            }
        }
        val registered = withContext(Dispatchers.IO) {
            runCatching {
                resolver.registerContentObserver(
                    Settings.Secure.getUriFor(ModuleConfig.SECURE_ASSISTANT),
                    false,
                    observer,
                )
                resolver.registerContentObserver(
                    Settings.Secure.getUriFor(ModuleConfig.SECURE_VOICE_INTERACTION_SERVICE),
                    false,
                    observer,
                )
                true
            }.getOrDefault(false)
        }
        try {
            awaitCancellation()
        } finally {
            assistantRefreshJob?.cancel()
            if (registered) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { resolver.unregisterContentObserver(observer) }
                }
            }
        }
    }
    val syncPowerTargetAfterManualAssistantChange: (AssistantRoleStatus) -> Unit = { updated ->
        val targetPrefs = prefs
        val autoConfigDefault = Prefs.Keys.BOOLEAN_DEFAULTS.getValue(Prefs.Keys.ASSISTANT_AUTO_CONFIG)
        if (targetPrefs != null &&
            targetPrefs.getBoolean(Prefs.Keys.ASSISTANT_AUTO_CONFIG, autoConfigDefault)
        ) {
            val mappedTarget = when (updated.owner) {
                DefaultAssistantOwner.ETA -> PowerAssistantTarget.ETA
                DefaultAssistantOwner.GEMINI -> PowerAssistantTarget.GEMINI
                DefaultAssistantOwner.OTHER,
                DefaultAssistantOwner.NONE -> PowerAssistantTarget.OEM
            }
            if (powerAssistantTarget != mappedTarget &&
                putStringSync(
                    prefs = targetPrefs,
                    key = Prefs.Keys.POWER_KEY_ASSISTANT_TARGET,
                    value = mappedTarget.persistedValue,
                )
            ) {
                powerAssistantTarget = mappedTarget
                enhancementHistory.recordCommittedTarget(mappedTarget)
            }
        }
    }
    var ownerBeforeAssistantSettings by remember { mutableStateOf<DefaultAssistantOwner?>(null) }
    val openAssistantSettings: () -> Unit = {
        val currentStatus = AssistantRoleHelper.queryStatus(context)
        val next = systemStatus.copy(assistantRoleStatus = currentStatus)
        cachedSettingsSystemStatus = next
        systemStatus = next
        if (AssistantRoleHelper.openSystemSettings(context)) {
            ownerBeforeAssistantSettings = currentStatus.owner
        } else {
            Toast.makeText(context, context.getString(R.string.settings_open_assistant_failed), Toast.LENGTH_SHORT).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val refreshSystemStatuses: (Boolean) -> Unit = { checkAssistantTransition ->
        coroutineScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                SettingsSystemStatus(
                    overlayGranted = android.provider.Settings.canDrawOverlays(context),
                    accessibilityGranted = isAgentAccessibilityEnabled(context),
                    accessibilityProtectionEnabled = AccessibilityProtectionClient.isEnabled(context),
                    assistantRoleStatus = AssistantRoleHelper.queryStatus(context),
                )
            }
            cachedSettingsSystemStatus = snapshot
            systemStatus = snapshot
            if (checkAssistantTransition) {
                val previousOwner = ownerBeforeAssistantSettings
                if (previousOwner != null) {
                    ownerBeforeAssistantSettings = null
                    if (snapshot.assistantRoleStatus.owner != previousOwner) {
                        syncPowerTargetAfterManualAssistantChange(snapshot.assistantRoleStatus)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        refreshSystemStatuses(false)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSystemStatuses(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(prefs) {
        val targetPrefs = prefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            when (key) {
                Prefs.Keys.POWER_KEY_ASSISTANT_TARGET,
                Prefs.Keys.POWER_KEY_TAKEOVER -> {
                    powerAssistantTarget = Prefs.powerAssistantTarget(changedPrefs)
                    scheduleAssistantRoleRefresh()
                }
                Prefs.Keys.ASSISTANT_AUTO_CONFIG -> {
                    scheduleAssistantRoleRefresh()
                }
            }
        }
        targetPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { targetPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(Unit) {
        var lastService = EtaApp.serviceInstance
        val initialCached = Prefs.cachedRemotePreferencesForUi(lastService)
        if (initialCached != null) {
            prefs = initialCached
            hasConnectedFramework = true
        }
        val listener = object : EtaApp.ServiceStateListener {
            override fun onServiceStateChanged(service: io.github.libxposed.service.XposedService?) {
                if (service === lastService && prefs != null) return
                lastService = service
                coroutineScope.launch {
                    val resolvedPrefs = withContext(Dispatchers.IO) {
                        Prefs.remotePreferencesForUi(service).also { connected ->
                            if (connected != null) {
                                enhancementHistory.captureConnected(connected)
                            }
                            Prefs.reconcileAgentPreferences(service)
                            RuntimeConfigRepository.ensureDefaults(service)
                        }
                    }
                    prefs = resolvedPrefs
                    if (resolvedPrefs != null) {
                        hasConnectedFramework = true
                    }
                }
            }
        }
        EtaApp.addServiceStateListener(listener, notifyImmediately = initialCached == null)
        onDispose { EtaApp.removeServiceStateListener(listener) }
    }
    val powerAssistantTargets = PowerAssistantTarget.entries
    val powerAssistantItems = powerAssistantTargets.map { target -> target.displayName() }
    val onPowerTargetSelect: (Int) -> Unit = { index ->
        val target = powerAssistantTargets.getOrNull(index)
        val targetPrefs = prefs
        if (target != null && targetPrefs != null) {
            if (putStringSync(
                    prefs = targetPrefs,
                    key = Prefs.Keys.POWER_KEY_ASSISTANT_TARGET,
                    value = target.persistedValue,
                )
            ) {
                powerAssistantTarget = target
                enhancementHistory.recordCommittedTarget(target)
                scheduleAssistantRoleRefresh()
            } else {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.settings_write_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val onAccessibilityProtectionToggle: (Boolean) -> Unit = { enabled ->
        if (!accessibilityProtectionPending) {
            accessibilityProtectionPending = true
            AccessibilityProtectionClient.setEnabled(
                context = context,
                enabled = enabled,
            ) { result ->
                accessibilityProtectionPending = false
                val updated = systemStatus.copy(
                    accessibilityProtectionEnabled = result.enabled,
                    accessibilityGranted = isAgentAccessibilityEnabled(context),
                )
                cachedSettingsSystemStatus = updated
                systemStatus = updated
                val failureMessage = when (result.status) {
                    AccessibilityProtectionClient.ControlStatus.APPLIED -> null
                    AccessibilityProtectionClient.ControlStatus.UNAVAILABLE ->
                        context.getString(R.string.accessibility_protection_unavailable)
                    AccessibilityProtectionClient.ControlStatus.REJECTED ->
                        context.getString(R.string.accessibility_protection_rejected)
                }
                if (failureMessage != null) {
                    Toast.makeText(
                        context.applicationContext,
                        failureMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val pageColors = CouiPageDefaults.themeColors()

            CouiScaffoldPage(
                title = stringResource(R.string.ui_set_up_7debf9),
                onBack = onBack,
                overlay = {
                    val activeUpdate = availableUpdate ?: retainedUpdate
                    CouiConfirmDialog(
                        visible = availableUpdate != null,
                        title = stringResource(R.string.ui_update_available_title),
                        message = activeUpdate?.let { update ->
                            stringResource(R.string.ui_update_available_message, update.version, appVersionName)
                        },
                        confirmText = stringResource(R.string.ui_update_go_download),
                        confirmEnabled = activeUpdate != null,
                        onConfirm = {
                            val target = activeUpdate
                            availableUpdate = null
                            if (target != null) openUrl(target.url)
                        },
                        onDismissRequest = { availableUpdate = null },
                        onDismissFinished = { if (availableUpdate == null) retainedUpdate = null },
                    )
                },
            ) {
                CouiPreferenceGroup(title = stringResource(R.string.settings_llm_providers)) {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_model_provider_e8c7f5),
                            summary = providerSummary,
                            icon = { CouiSettingsIcon(Icons.Rounded.Cloud, SettingsIconColors.Blue) },
                            onClick = { onNavigate(AppRoute.ModelProviders) },
                        )
                    }
                    item {
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.ui_deep_thinking_enabled_by_default_c032d6),
                            key = Prefs.Keys.AGENT_THINKING_ENABLED,
                            icon = Icons.Rounded.Psychology,
                            iconTint = SettingsIconColors.Blue,
                        )
                    }
                    if (effortOptions.size > 1) {
                        item {
                            CouiDropdownPreference(
                                title = defaultEffortTitle,
                                items = effortLabels,
                                selectedIndex = selectedEffortIndex,
                                onSelectedIndexChange = onDefaultEffortSelect,
                                icon = {
                                    CouiSettingsIcon(
                                        icon = Icons.Rounded.Tune,
                                        tint = SettingsIconColors.Blue,
                                        enabled = defaultEffortEnabled,
                                    )
                                },
                                enabled = defaultEffortEnabled,
                            )
                        }
                    } else {
                        item {
                            CouiPreference(
                                title = defaultEffortTitle,
                                summary = defaultEffortUnsupported,
                                icon = {
                                    CouiSettingsIcon(
                                        icon = Icons.Rounded.Tune,
                                        tint = SettingsIconColors.Blue,
                                        enabled = false,
                                    )
                                },
                                enabled = false,
                                showChevron = false,
                            )
                        }
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.settings_context_extensions)) {
                    item {
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.settings_auto_compaction),
                            key = Prefs.Keys.AGENT_AUTO_COMPACTION_ENABLED,
                            icon = Icons.Rounded.Layers,
                            iconTint = SettingsIconColors.Blue,
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.conversation_title_settings_title),
                            icon = { CouiSettingsIcon(Icons.Rounded.AutoAwesome, SettingsIconColors.Blue) },
                            onClick = { onNavigate(AppRoute.ConversationTitleSettings) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_memory_b55ff5),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.AutoMirrored.Rounded.MenuBook,
                                    SettingsIconColors.Orange,
                                )
                            },
                            onClick = { onNavigate(AppRoute.Memory) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_misc_route_subagents),
                            icon = { CouiSettingsIcon(Icons.Rounded.JoinLeft, SettingsIconColors.Blue) },
                            onClick = { onNavigate(AppRoute.Subagents) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.route_skills),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.ImportContacts,
                                    SettingsIconColors.Green,
                                )
                            },
                            onClick = { onNavigate(AppRoute.Skills) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.route_mcp_servers),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.AccountTree,
                                    SettingsIconColors.Blue,
                                )
                            },
                            onClick = { onNavigate(AppRoute.McpServers) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.route_scheduled_tasks),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.Schedule,
                                    SettingsIconColors.Blue,
                                )
                            },
                            onClick = { onNavigate(AppRoute.ScheduledTasks) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.conversation_dock_characters),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.SportsBar,
                                    SettingsIconColors.Orange,
                                )
                            },
                            onClick = { onNavigate(AppRoute.Characters) },
                        )
                    }
                }

                // ETA-K 定制：通知触发自动化
                CouiPreferenceGroup(title = "\u81ea\u52a8\u5316") {
                    item { NotificationTriggerSection() }
                }

                // ETA-K 定制：账号与连接（GitHub OAuth 设备流）
                CouiPreferenceGroup(title = "\u8d26\u53f7\u4e0e\u8fde\u63a5") {
                    item {
                        CouiPreference(
                            title = "GitHub \u8d26\u53f7",
                            summary = "OAuth \u8bbe\u5907\u7801\u6388\u6743\uff0c\u4ee4\u724c\u4ec5\u5b58\u672c\u673a",
                            icon = { CouiSettingsIcon(Icons.Rounded.AccountCircle, SettingsIconColors.Blue) },
                            onClick = { onNavigate(AppRoute.GitHubAccount) },
                        )
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.ui_tool_a72ef1)) {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.settings_tools_list),
                            icon = { CouiSettingsIcon(Icons.Rounded.Dashboard, SettingsIconColors.Green) },
                            onClick = { onNavigate(AppRoute.Tools) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.web_tools_settings_title),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.TravelExplore,
                                    SettingsIconColors.Blue,
                                )
                            },
                            onClick = { onNavigate(AppRoute.WebToolsSettings) },
                        )
                    }
                    item {
                        val imageGenSettings by remember { ImageGenSettingsRepository.settingsFlow() }
                            .collectAsState(initial = ImageGenSettings())
                        CouiPreference(
                            title = stringResource(R.string.image_gen_settings_title),
                            value = if (imageGenSettings.enabled) imageGenSettings.backend.shortName() else stringResource(R.string.ui_misc_not_enabled),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.Image,
                                    SettingsIconColors.Orange,
                                )
                            },
                            onClick = { onNavigate(AppRoute.ImageGenSettings) },
                        )
                    }
                    item {
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.settings_virtual_display_title),
                            key = Prefs.Keys.AGENT_VIRTUAL_DISPLAY_ENABLED,
                            icon = Icons.Rounded.Layers,
                            iconTint = SettingsIconColors.Blue,
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_linux_tool_environment_314d22),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.Inventory2,
                                    SettingsIconColors.Orange,
                                )
                            },
                            onClick = { onNavigate(AppRoute.LinuxEnvironment) },
                        )
                    }
                }

                CouiPreferenceGroup {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.capability_enhancements),
                            icon = { CouiSettingsIcon(Icons.Rounded.Security, SettingsIconColors.Blue) },
                            onClick = { onNavigate(AppRoute.SystemEnhance) },
                        )
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.ui_system_assistant_takes_over_f46043)) {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_eta_system_assistant_003e9b),
                            value = assistantRoleStatus.displayValue(),
                            valueColor = if (assistantRoleStatus.isDefaultAssistant) {
                                StatusSuccess
                            } else {
                                pageColors.summary
                            },
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.SupportAgent,
                                    SettingsIconColors.Green,
                                )
                            },
                            onClick = openAssistantSettings,
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.assistant_overlay_settings_title),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.ChatBubble,
                                    SettingsIconColors.Blue,
                                )
                            },
                            onClick = { onNavigate(AppRoute.AssistantOverlaySettings) },
                        )
                    }
                    if (prefs != null || hasConnectedFramework) {
                        item {
                            CouiDropdownPreference(
                                title = stringResource(R.string.ui_long_press_the_power_button_1958d0),
                                items = powerAssistantItems,
                                selectedIndex = powerAssistantTargets.indexOf(powerAssistantTarget),
                                onSelectedIndexChange = onPowerTargetSelect,
                                icon = {
                                    CouiSettingsIcon(
                                        icon = Icons.Rounded.PowerSettingsNew,
                                        tint = SettingsIconColors.Yellow,
                                        enabled = prefs != null,
                                    )
                                },
                                enabled = prefs != null,
                            )
                        }
                        item {
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_automatically_set_default_assistant_f86963),
                                key = Prefs.Keys.ASSISTANT_AUTO_CONFIG,
                                icon = Icons.Rounded.Settings,
                                iconTint = SettingsIconColors.Green,
                            )
                        }
                    }
                }

                if (prefs != null || hasConnectedFramework) {
                    CouiPreferenceGroup(title = stringResource(R.string.ui_search_in_one_turn_179584)) {
                        item {
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_long_press_on_the_gesture_bar_triggers_a_circle_to_s_b80117),
                                key = Prefs.Keys.GESTURE_BAR_CIRCLE_TO_SEARCH,
                                icon = Icons.Rounded.SwipeUp,
                                iconTint = SettingsIconColors.Blue,
                            )
                        }
                        item {
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_long_press_with_two_fingers_to_trigger_a_circle_sear_ab597a),
                                key = Prefs.Keys.DOUBLE_FINGER_CIRCLE_TO_SEARCH,
                                icon = Icons.Rounded.TouchApp,
                                iconTint = SettingsIconColors.Blue,
                            )
                        }
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.settings_general)) {
                    item {
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.settings_keep_screen_on),
                            key = Prefs.Keys.AGENT_KEEP_SCREEN_ON_ENABLED,
                            icon = Icons.Rounded.BrightnessHigh,
                            iconTint = SettingsIconColors.Orange,
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.appearance_title),
                            icon = { CouiSettingsIcon(Icons.Rounded.Palette, SettingsIconColors.Orange) },
                            onClick = { onNavigate(AppRoute.AppearanceSettings) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.custom_prompt_title),
                            summary = stringResource(R.string.custom_prompt_summary),
                            icon = { CouiSettingsIcon(Icons.Rounded.Description, SettingsIconColors.Blue) },
                            onClick = { onNavigate(AppRoute.CustomSystemPrompt) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.speech_settings_title),
                            icon = { CouiSettingsIcon(Icons.Rounded.Mic, SettingsIconColors.Blue) },
                            onClick = { onNavigate(AppRoute.SpeechSettings) },
                        )
                    }
                    item {
                        LanguagePreference(
                            iconTint = SettingsIconColors.Blue,
                            startAction = {
                                CouiSettingsIcon(Icons.Rounded.Language, SettingsIconColors.Blue)
                            },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.data_backup_title),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.Description,
                                    SettingsIconColors.Blue,
                                )
                            },
                            onClick = { onNavigate(AppRoute.DataBackup) },
                        )
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.ui_permissions_560165)) {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_floating_window_permissions_076b77),
                            value = stringResource(
                                if (overlayGranted) R.string.status_authorized else R.string.status_unauthorized,
                            ),
                            valueColor = if (overlayGranted) pageColors.summary else CouiTheme.colorScheme.error,
                            icon = { CouiSettingsIcon(Icons.Rounded.Layers, SettingsIconColors.Blue) },
                            onClick = {
                                if (!overlayGranted) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    val accEnabled = accessibilityGranted || AgentAccessibilityService.isAvailable()
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_accessibility_enhancement_tools_8fd257),
                            value = stringResource(
                                if (accEnabled) R.string.status_enabled else R.string.status_disabled,
                            ),
                            valueColor = if (accEnabled) pageColors.summary else pageColors.accent,
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.AccessibilityNew,
                                    SettingsIconColors.Blue,
                                )
                            },
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            },
                        )
                    }
                    if (prefs != null || hasConnectedFramework) {
                        item {
                            CouiSwitchPreference(
                                title = stringResource(R.string.ui_enforce_accessibility_55e838),
                                checked = accessibilityProtectionEnabled,
                                onCheckedChange = onAccessibilityProtectionToggle,
                                icon = {
                                    CouiSettingsIcon(
                                        icon = Icons.Rounded.VerifiedUser,
                                        tint = SettingsIconColors.Blue,
                                        enabled = prefs != null && !accessibilityProtectionPending,
                                    )
                                },
                                enabled = prefs != null && !accessibilityProtectionPending,
                            )
                        }
                    }
                }

                CouiPreferenceGroup(title = stringResource(R.string.ui_about_bed172)) {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_about_version_title),
                            summary = appVersionSummary,
                            icon = { CouiSettingsIcon(Icons.Rounded.Info, SettingsIconColors.Blue) },
                            showChevron = false,
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_about_update_title),
                            summary = if (checkingUpdate) {
                                stringResource(R.string.ui_about_update_checking)
                            } else {
                                null
                            },
                            icon = {
                                CouiSettingsIcon(
                                    icon = Icons.Rounded.SystemUpdate,
                                    tint = SettingsIconColors.Green,
                                    enabled = !checkingUpdate,
                                )
                            },
                            enabled = !checkingUpdate,
                            onClick = checkForUpdate,
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_about_feedback_title),
                            icon = {
                                CouiSettingsIcon(
                                    Icons.Rounded.BugReport,
                                    SettingsIconColors.Orange,
                                )
                            },
                            onClick = { openUrl("https://github.com/Mangi-11/Eta/issues") },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.ui_about_github_star_title),
                            summary = stringResource(R.string.ui_about_github_star_hint),
                            icon = { CouiSettingsIcon(Icons.Rounded.Code, SettingsIconColors.Blue) },
                            onClick = { openUrl("https://github.com/Mangi-11/Eta") },
                        )
                    }
                }

                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.ui_set_up_7debf9),
                onBack = onBack,
                overlay = {
                    val activeUpdate = availableUpdate ?: retainedUpdate
                    GlassDialog(
                        show = availableUpdate != null,
                        backdrop = currentGlassOverlayBackdrop(),
                        title = stringResource(R.string.ui_update_available_title),
                        summary = activeUpdate?.let { update ->
                            stringResource(R.string.ui_update_available_message, update.version, appVersionName)
                        },
                        onDismissRequest = { availableUpdate = null },
                        onDismissFinished = { if (availableUpdate == null) retainedUpdate = null },
                    ) {
                        MiuixDialogActions(
                            confirmText = stringResource(R.string.ui_update_go_download),
                            confirmEnabled = activeUpdate != null,
                            onCancel = { availableUpdate = null },
                            onConfirm = {
                                val target = activeUpdate
                                availableUpdate = null
                                if (target != null) openUrl(target.url)
                            },
                        )
                    }
                },
            ) {
                item(key = "section_agent_title") {
                    SmallTitle(stringResource(R.string.settings_llm_providers))
                }
                item(key = "section_agent") {
                    SettingsGroupCard {
                        ArrowPreference(
                            title = stringResource(R.string.ui_model_provider_e8c7f5),
                            summary = providerSummary,
                            startAction = { SettingsIcon(Icons.Rounded.Cloud) },
                            onClick = { onNavigate(AppRoute.ModelProviders) },
                        )
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.ui_deep_thinking_enabled_by_default_c032d6),
                            key = Prefs.Keys.AGENT_THINKING_ENABLED,
                            icon = Icons.Rounded.Psychology,
                            history = enhancementHistory,
                        )
                        if (effortOptions.size > 1) {
                            GlassDropdownPreference(
                                title = defaultEffortTitle,
                                items = effortLabels,
                                selectedIndex = selectedEffortIndex,
                                backdrop = currentGlassOverlayBackdrop(),
                                enabled = defaultEffortEnabled,
                                onSelectedIndexChange = onDefaultEffortSelect,
                                startAction = {
                                    SettingsIcon(
                                        icon = Icons.Rounded.Tune,
                                        enabled = defaultEffortEnabled,
                                    )
                                },
                            )
                        } else {
                            BasicComponent(
                                title = defaultEffortTitle,
                                summary = defaultEffortUnsupported,
                                startAction = {
                                    SettingsIcon(
                                        icon = Icons.Rounded.Tune,
                                        enabled = false,
                                    )
                                },
                                enabled = false,
                            )
                        }
                    }
                }

                item(key = "section_context_extensions_title") {
                    SmallTitle(stringResource(R.string.settings_context_extensions))
                }
                item(key = "section_context_extensions") {
                    SettingsGroupCard {
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.settings_auto_compaction),
                            key = Prefs.Keys.AGENT_AUTO_COMPACTION_ENABLED,
                            icon = Icons.Rounded.Layers,
                            history = enhancementHistory,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.conversation_title_settings_title),
                            startAction = { SettingsIcon(Icons.Rounded.AutoAwesome) },
                            onClick = { onNavigate(AppRoute.ConversationTitleSettings) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_memory_b55ff5),
                            startAction = { SettingsIcon(Icons.AutoMirrored.Rounded.MenuBook) },
                            onClick = { onNavigate(AppRoute.Memory) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_misc_route_subagents),
                            startAction = { SettingsIcon(Icons.Rounded.JoinLeft) },
                            onClick = { onNavigate(AppRoute.Subagents) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.route_skills),
                            startAction = { SettingsIcon(Icons.Rounded.ImportContacts) },
                            onClick = { onNavigate(AppRoute.Skills) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.route_mcp_servers),
                            startAction = { SettingsIcon(Icons.Rounded.AccountTree) },
                            onClick = { onNavigate(AppRoute.McpServers) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.route_scheduled_tasks),
                            startAction = { SettingsIcon(Icons.Rounded.Schedule) },
                            onClick = { onNavigate(AppRoute.ScheduledTasks) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.conversation_dock_characters),
                            startAction = { SettingsIcon(Icons.Rounded.SportsBar) },
                            onClick = { onNavigate(AppRoute.Characters) },
                        )
                    }
                }

                // ETA-K 定制：通知触发自动化
                item(key = "section_notification_trigger_title") {
                    SmallTitle("\u81ea\u52a8\u5316")
                }
                item(key = "section_notification_trigger") {
                    SettingsGroupCard { NotificationTriggerSection() }
                }

                // ETA-K 定制：账号与连接（GitHub OAuth 设备流）
                item(key = "section_accounts_title") {
                    SmallTitle("\u8d26\u53f7\u4e0e\u8fde\u63a5")
                }
                item(key = "section_accounts") {
                    SettingsGroupCard {
                        ArrowPreference(
                            title = "GitHub \u8d26\u53f7",
                            summary = "OAuth \u8bbe\u5907\u7801\u6388\u6743\uff0c\u4ee4\u724c\u4ec5\u5b58\u672c\u673a",
                            startAction = { SettingsIcon(Icons.Rounded.AccountCircle) },
                            onClick = { onNavigate(AppRoute.GitHubAccount) },
                        )
                    }
                }

                item(key = "section_tools_title") {
                    SmallTitle(stringResource(R.string.ui_tool_a72ef1))
                }
                item(key = "section_tools") {
                    SettingsGroupCard {
                        ArrowPreference(
                            title = stringResource(R.string.settings_tools_list),
                            startAction = { SettingsIcon(Icons.Rounded.Dashboard) },
                            onClick = { onNavigate(AppRoute.Tools) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.web_tools_settings_title),
                            startAction = { SettingsIcon(Icons.Rounded.TravelExplore) },
                            onClick = { onNavigate(AppRoute.WebToolsSettings) },
                        )
                        val imageGenSettings by remember { ImageGenSettingsRepository.settingsFlow() }
                            .collectAsState(initial = ImageGenSettings())
                        ArrowPreference(
                            title = stringResource(R.string.image_gen_settings_title),
                            summary = if (imageGenSettings.enabled) {
                                stringResource(R.string.ui_misc_enabled_with_value, imageGenSettings.backend.shortName())
                            } else {
                                stringResource(R.string.ui_misc_not_enabled)
                            },
                            startAction = { SettingsIcon(Icons.Rounded.Image) },
                            onClick = { onNavigate(AppRoute.ImageGenSettings) },
                        )
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.settings_virtual_display_title),
                            key = Prefs.Keys.AGENT_VIRTUAL_DISPLAY_ENABLED,
                            icon = Icons.Rounded.Layers,
                            history = enhancementHistory,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_linux_tool_environment_314d22),
                            startAction = { SettingsIcon(Icons.Rounded.Inventory2) },
                            onClick = { onNavigate(AppRoute.LinuxEnvironment) },
                        )
                    }
                }

                item(key = "system_enhancements") {
                    SettingsGroupCard {
                        ArrowPreference(
                            title = stringResource(R.string.capability_enhancements),
                            startAction = { SettingsIcon(Icons.Rounded.Security) },
                            onClick = { onNavigate(AppRoute.SystemEnhance) },
                        )
                    }
                }

                item(key = "section_assistant_takeover_title") {
                    SmallTitle(stringResource(R.string.ui_system_assistant_takes_over_f46043))
                }
                item(key = "section_assistant_takeover") {
                    SettingsGroupCard {
                        ArrowPreference(
                            title = stringResource(R.string.ui_eta_system_assistant_003e9b),
                            startAction = { SettingsIcon(Icons.Rounded.SupportAgent) },
                            endActions = {
                                Text(
                                    text = assistantRoleStatus.displayValue(),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = if (assistantRoleStatus.isDefaultAssistant) {
                                        StatusSuccess
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantActions
                                    },
                                )
                            },
                            onClick = openAssistantSettings,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.assistant_overlay_settings_title),
                            startAction = { SettingsIcon(Icons.Rounded.ChatBubble) },
                            onClick = { onNavigate(AppRoute.AssistantOverlaySettings) },
                        )
                        if (prefs != null || hasConnectedFramework) {
                            GlassDropdownPreference(
                                title = stringResource(R.string.ui_long_press_the_power_button_1958d0),
                                items = powerAssistantItems,
                                selectedIndex = powerAssistantTargets.indexOf(powerAssistantTarget),
                                backdrop = currentGlassOverlayBackdrop(),
                                onSelectedIndexChange = onPowerTargetSelect,
                                startAction = {
                                    SettingsIcon(
                                        icon = Icons.Rounded.PowerSettingsNew,
                                        enabled = prefs != null,
                                    )
                                },
                                enabled = prefs != null,
                            )
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_automatically_set_default_assistant_f86963),
                                key = Prefs.Keys.ASSISTANT_AUTO_CONFIG,
                                icon = Icons.Rounded.Settings,
                            )
                        }
                    }
                }

                if (prefs != null || hasConnectedFramework) {
                    item(key = "section_circle_to_search_title") {
                        SmallTitle(stringResource(R.string.ui_search_in_one_turn_179584))
                    }
                    item(key = "section_circle_to_search") {
                        SettingsGroupCard {
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_long_press_on_the_gesture_bar_triggers_a_circle_to_s_b80117),
                                key = Prefs.Keys.GESTURE_BAR_CIRCLE_TO_SEARCH,
                                icon = Icons.Rounded.SwipeUp,
                            )
                            SwitchPref(
                                context = context,
                                prefs = prefs,
                                title = stringResource(R.string.ui_long_press_with_two_fingers_to_trigger_a_circle_sear_ab597a),
                                key = Prefs.Keys.DOUBLE_FINGER_CIRCLE_TO_SEARCH,
                                icon = Icons.Rounded.TouchApp,
                            )
                        }
                    }
                }

                item(key = "section_general_title") {
                    SmallTitle(stringResource(R.string.settings_general))
                }
                item(key = "section_general") {
                    SettingsGroupCard {
                        SwitchPref(
                            context = context,
                            prefs = agentPrefs,
                            title = stringResource(R.string.settings_keep_screen_on),
                            key = Prefs.Keys.AGENT_KEEP_SCREEN_ON_ENABLED,
                            icon = Icons.Rounded.BrightnessHigh,
                            history = enhancementHistory,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.appearance_title),
                            startAction = { SettingsIcon(Icons.Rounded.Palette) },
                            onClick = { onNavigate(AppRoute.AppearanceSettings) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.custom_prompt_title),
                            startAction = {
                                SettingsIcon(
                                    icon = Icons.Rounded.Description,
                                )
                            },
                            onClick = { onNavigate(AppRoute.CustomSystemPrompt) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.speech_settings_title),
                            startAction = { SettingsIcon(Icons.Rounded.Mic) },
                            onClick = { onNavigate(AppRoute.SpeechSettings) },
                        )
                        LanguagePreference(
                            startAction = { SettingsIcon(Icons.Rounded.Language) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.data_backup_title),
                            startAction = { SettingsIcon(Icons.Rounded.Description) },
                            onClick = { onNavigate(AppRoute.DataBackup) },
                        )
                    }
                }

                item(key = "section_permissions_title") {
                    SmallTitle(stringResource(R.string.ui_permissions_560165))
                }
                item(key = "section_permissions") {
                    SettingsGroupCard {
                        ArrowPreference(
                            title = stringResource(R.string.ui_floating_window_permissions_076b77),
                            startAction = { SettingsIcon(Icons.Rounded.Layers) },
                            endActions = {
                                Text(
                                    text = stringResource(
                                        if (overlayGranted) R.string.status_authorized else R.string.status_unauthorized,
                                    ),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = if (overlayGranted) {
                                        MiuixTheme.colorScheme.onSurfaceVariantActions
                                    } else {
                                        MiuixTheme.colorScheme.error
                                    },
                                )
                            },
                            onClick = {
                                if (!overlayGranted) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_accessibility_enhancement_tools_8fd257),
                            startAction = { SettingsIcon(Icons.Rounded.AccessibilityNew) },
                            endActions = {
                                val enabled = accessibilityGranted || AgentAccessibilityService.isAvailable()
                                Text(
                                    text = stringResource(
                                        if (enabled) R.string.status_enabled else R.string.status_disabled,
                                    ),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = if (enabled) {
                                        MiuixTheme.colorScheme.onSurfaceVariantActions
                                    } else {
                                        MiuixTheme.colorScheme.primary
                                    },
                                )
                            },
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                    )
                                }
                            },
                        )
                        if (prefs != null || hasConnectedFramework) {
                            SwitchPreference(
                                title = stringResource(R.string.ui_enforce_accessibility_55e838),
                                checked = accessibilityProtectionEnabled,
                                onCheckedChange = onAccessibilityProtectionToggle,
                                startAction = {
                                    SettingsIcon(
                                        icon = Icons.Rounded.VerifiedUser,
                                        enabled = prefs != null && !accessibilityProtectionPending,
                                    )
                                },
                                enabled = prefs != null && !accessibilityProtectionPending,
                            )
                        }
                    }
                }

                item(key = "section_about_title") {
                    SmallTitle(stringResource(R.string.ui_about_bed172))
                }
                item(key = "section_about") {
                    SettingsGroupCard {
                        BasicComponent(
                            title = stringResource(R.string.ui_about_version_title),
                            summary = appVersionSummary,
                            startAction = { SettingsIcon(Icons.Rounded.Info) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_about_update_title),
                            summary = if (checkingUpdate) {
                                stringResource(R.string.ui_about_update_checking)
                            } else {
                                null
                            },
                            startAction = {
                                SettingsIcon(
                                    icon = Icons.Rounded.SystemUpdate,
                                    enabled = !checkingUpdate,
                                )
                            },
                            enabled = !checkingUpdate,
                            onClick = checkForUpdate,
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_about_feedback_title),
                            startAction = { SettingsIcon(Icons.Rounded.BugReport) },
                            onClick = { openUrl("https://github.com/Mangi-11/Eta/issues") },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.ui_about_github_star_title),
                            summary = stringResource(R.string.ui_about_github_star_hint),
                            startAction = { SettingsIcon(Icons.Rounded.Code) },
                            onClick = { openUrl("https://github.com/Mangi-11/Eta") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantRoleStatus.displayValue(): String = when (owner) {
    DefaultAssistantOwner.ETA -> "Eta"
    DefaultAssistantOwner.GEMINI -> "Gemini"
    DefaultAssistantOwner.OTHER -> ownerLabel ?: stringResource(R.string.assistant_role_status_other)
    DefaultAssistantOwner.NONE -> stringResource(R.string.assistant_role_status_unset)
}

@Composable
private fun SwitchPref(
    context: Context,
    prefs: SharedPreferences?,
    title: String,
    summary: String? = null,
    key: String,
    icon: ImageVector,
    iconTint: Color = Color.Unspecified,
    history: EnhancementSettingsHistory = remember(context.applicationContext) {
        EnhancementSettingsHistory(context)
    },
) {
    val enabled = prefs != null
    val default = Prefs.Keys.BOOLEAN_DEFAULTS[key] ?: true
    var checked by remember(prefs, key) {
        mutableStateOf(prefs?.getBoolean(key, default) ?: history.checked(key, default))
    }
    DisposableEffect(prefs, key) {
        val targetPrefs = prefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, changedKey ->
            if (changedKey == key) {
                checked = changedPrefs.getBoolean(key, default)
            }
        }
        targetPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { targetPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val onToggle: (Boolean) -> Unit = { value ->
        val targetPrefs = prefs
        if (targetPrefs != null) {
            if (putBooleanSync(targetPrefs, key, value)) {
                checked = value
                history.recordCommittedBoolean(key, value)
                if (key in Prefs.Keys.LOCAL_AGENT_KEYS) {
                    Prefs.reconcileAgentPreferences(EtaApp.serviceInstance)
                }
            } else {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.settings_write_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiSwitchPreference(
                title = title,
                summary = summary,
                checked = checked,
                onCheckedChange = onToggle,
                icon = { CouiSettingsIcon(icon = icon, tint = iconTint, enabled = enabled) },
                enabled = enabled,
            )
        }
        AppearanceUiStyle.MIUIX -> {
            SwitchPreference(
                title = title,
                summary = summary,
                checked = checked,
                onCheckedChange = onToggle,
                startAction = {
                    SettingsIcon(icon = icon, enabled = enabled)
                },
                enabled = enabled,
            )
        }
    }
}

private fun putBooleanSync(
    prefs: SharedPreferences,
    key: String,
    value: Boolean,
): Boolean =
    runCatching { prefs.edit().putBoolean(key, value).commit() }.getOrDefault(false)

private fun putStringSync(
    prefs: SharedPreferences,
    key: String,
    value: String,
): Boolean =
    runCatching { prefs.edit().putString(key, value).commit() }.getOrDefault(false)

@Composable
private fun PowerAssistantTarget.displayName(): String =
    when (this) {
        PowerAssistantTarget.OEM -> stringResource(R.string.power_assistant_system_default)
        PowerAssistantTarget.GEMINI -> "Gemini"
        PowerAssistantTarget.ETA -> "Eta"
    }

private fun isAgentAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(
        context,
        AgentAccessibilityService::class.java,
    ).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

/**
 * 通知触发自动化管理：收到匹配规则的通知时，把「通知摘要 + 动作指令」提交给 agent。
 *
 * 规则保存在 SharedPreferences（JSON 数组），条目预期个位数，不引入数据库。
 */
@Composable
internal fun NotificationTriggerSection() {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(NotificationTriggerStore.load(context)) }
    var showList by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NotificationTriggerRule?>(null) }
    var triggerLog by remember { mutableStateOf(NotificationTriggerLogStore.load(context)) }
    var showTriggerLog by remember { mutableStateOf(false) }

    fun update(next: List<NotificationTriggerRule>) {
        NotificationTriggerStore.save(context, next)
        rules = next
    }

    // 外层已经是 Group（COUI 玻璃容器）；再套一层 miuix Card 会变成不透明黑色块。
    CouiPreference(
        title = "通知触发自动化",
        summary = if (rules.isEmpty()) {
            "未配置规则；命中通知时自动提交动作指令"
        } else {
            "${rules.count { it.enabled }} 条规则启用"
        },
        icon = { CouiSettingsIcon(Icons.Rounded.Notifications, SettingsIconColors.Blue) },
        onClick = { showList = true },
    )
    CouiPreference(
        title = "触发记录",
        summary = if (triggerLog.isEmpty()) {
            "还没有触发过；命中规则时这里会留下记录"
        } else {
            "最近 ${triggerLog.size} 条触发记录"
        },
        icon = { CouiSettingsIcon(Icons.Rounded.History, SettingsIconColors.Orange) },
        onClick = {
            triggerLog = NotificationTriggerLogStore.load(context)
            showTriggerLog = true
        },
    )

    if (showList) {
        if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
            CouiNoticeDialog(
                visible = true,
                title = "通知触发规则",
                message = if (rules.isEmpty()) {
                    "还没有规则。点「添加规则」创建：来源包名与关键词都命中时，通知会转成动作指令交给 agent。"
                } else {
                    "共 ${rules.size} 条规则；开关控制是否生效"
                },
                confirmText = "关闭",
                onDismissRequest = { showList = false },
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    rules.forEach { rule ->
                        CouiSwitchPreference(
                            title = rule.name,
                            summary = ruleConditionText(rule) + "\n动作：" + rule.actionPrompt,
                            checked = rule.enabled,
                            onCheckedChange = { enabled ->
                                update(
                                    rules.map {
                                        if (it.id == rule.id) it.copy(enabled = enabled) else it
                                    },
                                )
                            },
                        )
                        CouiPreference(
                            title = "删除「${rule.name}」",
                            titleColor = StatusError,
                            onClick = { pendingDelete = rule },
                        )
                    }
                    if (rules.isEmpty()) {
                        CouiText(
                            text = "还没有规则",
                            kind = CouiTextKind.GroupFooter,
                            color = CouiPageDefaults.themeColors().summary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    CouiPreference(
                        title = "添加规则",
                        summary = "包名、标题、内容三个条件全部命中才触发",
                        onClick = {
                            showList = false
                            showEditor = true
                        },
                    )
                }
            }
        } else {
            WindowDialog(
            show = true,
            title = "通知触发规则",
            summary = if (rules.isEmpty()) {
                "还没有规则。点「添加规则」创建：来源包名与关键词都命中时，通知会转成动作指令交给 agent。"
            } else {
                "共 ${rules.size} 条规则；开关控制是否生效"
            },
            onDismissRequest = { showList = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rules.forEach { rule ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = rule.name,
                                    style = MiuixTheme.textStyles.body1,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        update(rules.map {
                                            if (it.id == rule.id) it.copy(enabled = enabled) else it
                                        })
                                    },
                                )
                            }
                            Text(
                                text = buildString {
                                    append("包名：")
                                    append(rule.packageName.ifBlank { "任意" })
                                    if (rule.titleKeyword.isNotBlank()) {
                                        append(" · 标题含「")
                                        append(rule.titleKeyword)
                                        append("」")
                                    }
                                    if (rule.textKeyword.isNotBlank()) {
                                        append(" · 内容含「")
                                        append(rule.textKeyword)
                                        append("」")
                                    }
                                },
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                text = "动作：${rule.actionPrompt}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            TextButton(
                                text = "删除",
                                onClick = { pendingDelete = rule },
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (rules.isEmpty()) {
                    Text(
                        text = "还没有规则",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                TextButton(
                    text = "添加规则",
                    onClick = {
                        showList = false
                        showEditor = true
                    },
                )
            }
            MiuixDialogActions(
                confirmText = "关闭",
                onCancel = { showList = false },
                onConfirm = { showList = false },
            )
        }
        }
    }

    if (showEditor) {
        RuleEditorDialog(
            onDismiss = { showEditor = false },
            onSaved = { rule ->
                update(rules + rule)
                showEditor = false
            },
        )
    }

    if (showTriggerLog) {
        val logSummary = if (triggerLog.isEmpty()) {
            "还没有触发记录。规则命中并提交动作时，会在这里留下时间、来源与通知摘要。"
        } else {
            "共 ${triggerLog.size} 条；只记录触发事实，执行结果请看对应会话。"
        }
        if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
            CouiNoticeDialog(
                visible = true,
                title = "触发记录",
                message = logSummary,
                confirmText = "关闭",
                onDismissRequest = { showTriggerLog = false },
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    triggerLog.forEach { entry ->
                        CouiPreference(
                            title = entry.ruleName.ifBlank { "（未命名规则）" },
                            summary = triggerLogSummary(entry),
                        )
                    }
                    if (triggerLog.isNotEmpty()) {
                        CouiPreference(
                            title = "清空记录",
                            titleColor = StatusError,
                            onClick = {
                                NotificationTriggerLogStore.clear(context)
                                triggerLog = emptyList()
                            },
                        )
                    }
                }
            }
        } else {
            WindowDialog(
                show = true,
                title = "触发记录",
                summary = logSummary,
                onDismissRequest = { showTriggerLog = false },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    triggerLog.take(20).forEach { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = entry.ruleName.ifBlank { "（未命名规则）" },
                                    style = MiuixTheme.textStyles.body1,
                                )
                                Text(
                                    text = triggerLogSummary(entry),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    if (triggerLog.isNotEmpty()) {
                        TextButton(
                            text = "清空记录",
                            onClick = {
                                NotificationTriggerLogStore.clear(context)
                                triggerLog = emptyList()
                            },
                        )
                    }
                }
                MiuixDialogActions(
                    confirmText = "关闭",
                    onCancel = { showTriggerLog = false },
                    onConfirm = { showTriggerLog = false },
                )
            }
        }
    }

    pendingDelete?.let { rule ->
        if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
            CouiConfirmDialog(
                visible = true,
                title = "删除规则「${rule.name}」？",
                message = "删除后，匹配该规则的通知将不再触发自动化。",
                confirmText = "删除",
                destructive = true,
                onConfirm = {
                    update(rules.filterNot { it.id == rule.id })
                    pendingDelete = null
                },
                onDismissRequest = { pendingDelete = null },
            )
        } else {
            WindowDialog(
                show = true,
                title = "删除规则「${rule.name}」？",
                summary = "删除后，匹配该规则的通知将不再触发自动化。",
                onDismissRequest = { pendingDelete = null },
            ) {
                MiuixDialogActions(
                    confirmText = "删除",
                    destructive = true,
                    onCancel = { pendingDelete = null },
                    onConfirm = {
                        update(rules.filterNot { it.id == rule.id })
                        pendingDelete = null
                    },
                )
            }
        }
    }
}

/** 触发记录的单行摘要：时间 · 来源 · 标题/正文摘要。 */
private fun triggerLogSummary(entry: NotificationTriggerLogEntry): String {
    val stamp = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(entry.at))
    val brief = listOf(entry.title, entry.text).filter { it.isNotBlank() }.joinToString(" ")
    return listOf(stamp, entry.packageName, brief.take(40))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/** 把规则的三个匹配条件拼成一行可读描述。 */
private fun ruleConditionText(rule: NotificationTriggerRule): String = buildString {
    append("包名：")
    append(rule.packageName.ifBlank { "任意" })
    if (rule.titleKeyword.isNotBlank()) {
        append(" · 标题含「")
        append(rule.titleKeyword)
        append("」")
    }
    if (rule.textKeyword.isNotBlank()) {
        append(" · 内容含「")
        append(rule.textKeyword)
        append("」")
    }
}

@Composable
private fun RuleEditorDialog(onDismiss: () -> Unit, onSaved: (NotificationTriggerRule) -> Unit) {
    var name by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var titleKeyword by remember { mutableStateOf("") }
    var textKeyword by remember { mutableStateOf("") }
    var actionPrompt by remember { mutableStateOf("") }

    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        CouiConfirmDialog(
            visible = true,
            title = "添加通知触发规则",
            message = "三个匹配条件是「且」关系：全部命中才触发。触发时通知的包名、标题、内容会附在指令前交给 agent。",
            confirmText = "保存",
            confirmEnabled = name.isNotBlank() && actionPrompt.isNotBlank(),
            onConfirm = {
                onSaved(
                    NotificationTriggerRule(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        packageName = packageName.trim(),
                        titleKeyword = titleKeyword.trim(),
                        textKeyword = textKeyword.trim(),
                        actionPrompt = actionPrompt.trim(),
                    ),
                )
            },
            onDismissRequest = onDismiss,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CouiTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "规则名称",
                )
                CouiTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = "来源包名（留空 = 任意）",
                )
                CouiTextField(
                    value = titleKeyword,
                    onValueChange = { titleKeyword = it },
                    label = "标题关键词（留空 = 不限）",
                )
                CouiTextField(
                    value = textKeyword,
                    onValueChange = { textKeyword = it },
                    label = "内容关键词（留空 = 不限）",
                )
                CouiTextField(
                    value = actionPrompt,
                    onValueChange = { actionPrompt = it },
                    label = "动作指令（交给 agent 的 prompt）",
                    singleLine = false,
                    minLines = 3,
                )
            }
        }
        return
    }

    WindowDialog(
        show = true,
        title = "添加通知触发规则",
        summary = "三个匹配条件是「且」关系：全部命中才触发。触发时通知的包名、标题、内容会附在指令前交给 agent。",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "规则名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = "来源包名（留空 = 任意）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = titleKeyword,
                onValueChange = { titleKeyword = it },
                label = "标题关键词（留空 = 不限）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = textKeyword,
                onValueChange = { textKeyword = it },
                label = "内容关键词（留空 = 不限）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = actionPrompt,
                onValueChange = { actionPrompt = it },
                label = "动作指令（交给 agent 的 prompt）",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiuixDialogActions(
            confirmText = "保存",
            confirmEnabled = name.isNotBlank() && actionPrompt.isNotBlank(),
            onCancel = onDismiss,
            onConfirm = {
                onSaved(
                    NotificationTriggerRule(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        packageName = packageName.trim(),
                        titleKeyword = titleKeyword.trim(),
                        textKeyword = textKeyword.trim(),
                        actionPrompt = actionPrompt.trim(),
                    ),
                )
            },
        )
    }
}
