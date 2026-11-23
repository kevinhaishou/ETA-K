package io.github.mangi.eta.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.customization.CustomizationStore
import io.github.mangi.eta.customization.PromptPreset
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.screens.characters.CharacterTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.mangi.eta.ui.components.coui.CouiPreferenceGroup
import io.github.mangi.eta.ui.components.coui.CouiSectionTitle
import androidx.compose.foundation.clickable
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme as CouiTheme
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.SwitchPreference
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column

/**
 * 自定义系统提示词。
 *
 * 生效内容依旧是单条（输入即保存），额外提供「提示词库」：可以从文件一次导入多条，
 * 列表里点哪条就把那条的内容写进生效字段。运行时读取路径没有变化。
 */
@Composable
internal fun CustomSystemPromptScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by CustomizationStore.revision.collectAsState()
    var draft by remember { mutableStateOf(CustomizationStore.systemPrompt()) }
    var pendingDelete by remember { mutableStateOf<PromptPreset?>(null) }
    val presets = remember(revision) { CustomizationStore.promptPresets() }
    val activeId = remember(revision) { CustomizationStore.activePresetId() }
    val promptEnabled = remember(revision) { CustomizationStore.promptEnabled() }
    val promptMode = remember(revision) { CustomizationStore.promptMode() }
    val presetModified = remember(revision) { CustomizationStore.promptModifiedFromPreset() }
    var naming by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var nameTarget by remember { mutableStateOf<PromptPreset?>(null) }
    var nameText by remember { mutableStateOf("") }

    LaunchedEffect(revision) {
        draft = CustomizationStore.systemPrompt()
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val picked = uris.orEmpty()
        if (picked.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                picked.mapNotNull { uri -> readPromptDocument(context, uri) }
            }
            if (entries.isEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.prompt_library_import_unreadable, picked.size),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val count = CustomizationStore.importPromptPresets(entries)
            val message = if (count > 0) {
                context.getString(R.string.prompt_library_imported, count)
            } else {
                context.getString(R.string.prompt_library_import_failed)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val onImport: () -> Unit = {
        runCatching {
            importer.launch(arrayOf("*/*"))
        }.onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.prompt_library_import_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val onChange: (String) -> Unit = { value ->
        draft = value
        CustomizationStore.setSystemPrompt(value)
    }

    val onSaveCurrent: () -> Unit = {
        nameTarget = null
        nameText = context.getString(R.string.prompt_library_saved_name)
        naming = true
    }
    val onNameConfirm: () -> Unit = {
        val name = nameText.trim()
        if (name.isNotEmpty()) {
            val target = nameTarget
            if (target == null) {
                val saved = CustomizationStore.saveCurrentAsPreset(name)
                Toast.makeText(
                    context,
                    context.getString(if (saved) R.string.prompt_library_saved_ok else R.string.prompt_library_import_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                CustomizationStore.renamePromptPreset(target.id, name)
            }
        }
        naming = false
    }

    val replaceMsg = "替换后不再发送内置提示词，工具调用格式等规则会丢失，可能导致工具调用失败。确定切换？"
    val onReplaceOk: () -> Unit = { CustomizationStore.setPromptMode(CustomizationStore.PromptMode.REPLACE); confirmReplace = false }
    var previewText by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(previewing, revision) {
        if (previewing) {
            val builtin = runCatching { io.github.mangi.eta.data.repository.RuntimeConfigRepository.selectedProvider()?.systemPrompt.orEmpty() }.getOrDefault("")
            previewText = CustomizationStore.composeSystemPrompt(builtin).ifBlank { "（最终提示词为空）" }
        }
    }
    if (confirmReplace || previewing) {
        val t = if (confirmReplace) "切换为替换模式" else "最终提示词预览 · ${previewText.length} 字"
        val m = if (confirmReplace) replaceMsg else previewText
        val ok: () -> Unit = if (confirmReplace) onReplaceOk else ({ previewing = false })
        val dismiss: () -> Unit = { confirmReplace = false; previewing = false }
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> CouiConfirmDialog(
                visible = true, title = t, message = m,
                confirmText = if (confirmReplace) "切换" else "关闭",
                confirmEnabled = true, onConfirm = ok, onDismissRequest = dismiss,
            ) {}
            AppearanceUiStyle.MIUIX -> GlassDialog(
                show = true, backdrop = currentGlassOverlayBackdrop(), title = t, onDismissRequest = dismiss,
            ) {
                Column {
                    Text(text = m, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(bottom = 12.dp))
                    MiuixDialogActions(confirmText = if (confirmReplace) "切换" else "关闭", onCancel = dismiss, onConfirm = ok)
                }
            }
        }
    }

    if (naming) {
        val dialogTitle = if (nameTarget == null) "保存为预设" else "重命名预设"
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> CouiConfirmDialog(
                visible = true,
                title = dialogTitle,
                message = null,
                confirmText = "保存",
                confirmEnabled = nameText.isNotBlank(),
                onConfirm = onNameConfirm,
                onDismissRequest = { naming = false },
            ) {
                CharacterTextField(label = "预设名称", value = nameText, onChange = { nameText = it }, enabled = true, singleLine = true)
            }
            AppearanceUiStyle.MIUIX -> GlassDialog(
                show = true,
                backdrop = currentGlassOverlayBackdrop(),
                title = dialogTitle,
                onDismissRequest = { naming = false },
            ) {
                Column {
                    CharacterTextField(label = "预设名称", value = nameText, onChange = { nameText = it }, enabled = true, singleLine = true)
                    MiuixDialogActions(
                        confirmText = "保存",
                        onCancel = { naming = false },
                        onConfirm = onNameConfirm,
                    )
                }
            }
        }
    }

    // 删除预设确认
    pendingDelete?.let { target ->
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiConfirmDialog(
                    visible = true,
                    title = "\u5220\u9664\u9884\u8bbe",
                    message = "\u5220\u9664\u300c${target.name}\u300d\uff1f\u5f53\u524d\u751f\u6548\u7684\u63d0\u793a\u8bcd\u4e0d\u4f1a\u88ab\u6e05\u7a7a\u3002",
                    confirmText = "\u5220\u9664",
                    destructive = true,
                    onConfirm = {
                        CustomizationStore.deletePromptPreset(target.id)
                        pendingDelete = null
                    },
                    onDismissRequest = { pendingDelete = null },
                )
            }
            AppearanceUiStyle.MIUIX -> {
                GlassDialog(
                    show = true,
                    backdrop = currentGlassOverlayBackdrop(),
                    title = "\u5220\u9664\u9884\u8bbe",
                    summary = "\u5220\u9664\u300c${target.name}\u300d\uff1f\u5f53\u524d\u751f\u6548\u7684\u63d0\u793a\u8bcd\u4e0d\u4f1a\u88ab\u6e05\u7a7a\u3002",
                    onDismissRequest = { pendingDelete = null },
                ) {
                    MiuixDialogActions(
                        confirmText = "\u5220\u9664",
                        destructive = true,
                        onCancel = { pendingDelete = null },
                        onConfirm = {
                            CustomizationStore.deletePromptPreset(target.id)
                            pendingDelete = null
                        },
                    )
                }
            }
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.custom_prompt_title),
                onBack = onBack,
            ) {
                CouiPreferenceGroup(title = "注入设置") {
                    item(key = "prompt_enabled") {
                        CouiSwitchPreference(
                            title = "启用自定义提示词",
                            summary = if (promptEnabled) "已启用" else "已停用，内容保留但不生效",
                            checked = promptEnabled,
                            onCheckedChange = { CustomizationStore.setPromptEnabled(it) },
                        )
                    }
                    item(key = "prompt_preview") {
                        CouiPreference(title = "预览最终提示词", summary = "查看合成后发给模型的系统提示词", onClick = { previewing = true })
                    }
                    PROMPT_MODE_OPTIONS.forEach { (mode, label, desc) ->
                        item(key = "mode_${mode.name}") {
                            CouiPreference(
                                title = if (mode == promptMode) "✓ $label" else label,
                                summary = desc,
                                enabled = promptEnabled,
                                onClick = { if (mode == CustomizationStore.PromptMode.REPLACE && promptMode != mode) confirmReplace = true else CustomizationStore.setPromptMode(mode) },
                            )
                        }
                    }
                }
                CouiPreferenceGroup(title = stringResource(R.string.prompt_library_group)) {
                    item(key = "import") {
                        CouiPreference(
                            title = stringResource(R.string.prompt_library_import_title),
                            summary = stringResource(R.string.prompt_library_import_summary),
                            onClick = onImport,
                        )
                    }
                    item(key = "save_current") {
                        CouiPreference(
                            title = stringResource(R.string.prompt_library_save_current),
                            summary = stringResource(R.string.prompt_library_save_current_summary),
                            onClick = onSaveCurrent,
                        )
                    }
                }
                if (presets.isEmpty()) {
                    CouiPreferenceGroup {
                        item(key = "empty") {
                            CouiPreference(
                                title = stringResource(R.string.prompt_library_empty),
                                summary = null,
                                enabled = false,
                            )
                        }
                    }
                } else {
                    CouiPreferenceGroup(title = stringResource(R.string.prompt_library_list)) {
                        presets.forEach { preset ->
                            item(key = "preset_${preset.id}") {
                                CouiPreference(
                                    title = presetTitle(preset, preset.id == activeId, presetModified),
                                    summary = presetSummary(preset.content),
                                    onClick = { CustomizationStore.applyPromptPreset(preset.id) },
                                    trailing = {
                                        Row {
                                        Text(
                                            text = "改名",
                                            color = MiuixTheme.colorScheme.primary,
                                            style = MiuixTheme.textStyles.footnote2,
                                            modifier = Modifier
                                                .clickable { nameTarget = preset; nameText = preset.name; naming = true }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                        Text(
                                            text = "\u5220\u9664",
                                            color = CouiTheme.colorScheme.error,
                                            style = MiuixTheme.textStyles.footnote2,
                                            modifier = Modifier
                                                .clickable { pendingDelete = preset }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                CouiSectionTitle(stringResource(R.string.custom_prompt_summary) + " · ${draft.length} 字")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                ) {
                    CharacterTextField(
                        label = stringResource(R.string.custom_prompt_hint),
                        value = draft,
                        onChange = onChange,
                        enabled = true,
                        minLines = 8,
                    )
                }
                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.custom_prompt_title),
                onBack = onBack,
            ) {
                item(key = "prompt_inject") {
                    SmallTitle(text = "注入设置")
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "启用自定义提示词",
                            summary = if (promptEnabled) "已启用" else "已停用，内容保留但不生效",
                            checked = promptEnabled,
                            onCheckedChange = { CustomizationStore.setPromptEnabled(it) },
                        )
                        BasicComponent(title = "预览最终提示词", summary = "查看合成后发给模型的系统提示词", onClick = { previewing = true })
                        PROMPT_MODE_OPTIONS.forEach { (mode, label, desc) ->
                            BasicComponent(
                                title = if (mode == promptMode) "✓ $label" else label,
                                summary = desc,
                                onClick = { if (promptEnabled) { if (mode == CustomizationStore.PromptMode.REPLACE && promptMode != mode) confirmReplace = true else CustomizationStore.setPromptMode(mode) } },
                            )
                        }
                    }
                }
                item(key = "prompt_library_actions") {
                    SmallTitle(text = stringResource(R.string.prompt_library_group))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.prompt_library_import_title),
                            summary = stringResource(R.string.prompt_library_import_summary),
                            onClick = onImport,
                        )
                        BasicComponent(
                            title = stringResource(R.string.prompt_library_save_current),
                            summary = stringResource(R.string.prompt_library_save_current_summary),
                            onClick = onSaveCurrent,
                        )
                    }
                }
                if (presets.isEmpty()) {
                    item(key = "prompt_library_empty") {
                        SmallTitle(text = stringResource(R.string.prompt_library_empty))
                    }
                } else {
                    item(key = "prompt_library_list") {
                        SmallTitle(text = stringResource(R.string.prompt_library_list))
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            presets.forEach { preset ->
                                BasicComponent(
                                    title = presetTitle(preset, preset.id == activeId, presetModified),
                                    summary = presetSummary(preset.content),
                                    onClick = { CustomizationStore.applyPromptPreset(preset.id) },
                                    endActions = {
                                        Row {
                                        Text(
                                            text = "改名",
                                            color = MiuixTheme.colorScheme.primary,
                                            style = MiuixTheme.textStyles.footnote2,
                                            modifier = Modifier
                                                .clickable { nameTarget = preset; nameText = preset.name; naming = true }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                        Text(
                                            text = "\u5220\u9664",
                                            color = MiuixTheme.colorScheme.error,
                                            style = MiuixTheme.textStyles.footnote2,
                                            modifier = Modifier
                                                .clickable { pendingDelete = preset }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "custom_prompt") {
                    SmallTitle(text = stringResource(R.string.custom_prompt_summary) + " · ${draft.length} 字")
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        CharacterTextField(
                            label = stringResource(R.string.custom_prompt_hint),
                            value = draft,
                            onChange = onChange,
                            enabled = true,
                            minLines = 8,
                        )
                    }
                }
            }
        }
    }
}

private fun presetTitle(preset: PromptPreset, active: Boolean, modified: Boolean): String = when {
    active && modified -> "${preset.name} · 已修改"
    active -> "${preset.name} · 当前使用"
    else -> preset.name
}

private val PROMPT_MODE_OPTIONS = listOf(
    Triple(CustomizationStore.PromptMode.APPEND, "追加", "附加在内置提示词之后，冲突时由模型自行判断"),
    Triple(CustomizationStore.PromptMode.STYLE, "风格优先（推荐）", "角色、风格、语气听自定义的，工具调用格式仍按内置"),
    Triple(CustomizationStore.PromptMode.PRIORITY, "完全优先", "与内置冲突时一律以自定义为准，可能影响工具调用"),
    Triple(CustomizationStore.PromptMode.REPLACE, "替换", "只使用自定义内容，不发送内置提示词"),
)

private fun presetSummary(content: String): String {
    val flat = content.replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= 60) flat else flat.take(60) + "…"
}

/** 读取选中的文件：文件名（去扩展名）作为预设名称，全文作为内容。 */
private fun readPromptDocument(context: Context, uri: Uri): Pair<String, String>? {
    val displayName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
    val text = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
    if (text.isNullOrBlank()) return null
    val name = displayName
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.prompt_library_default_name)
    return name to text
}
