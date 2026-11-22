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
import import androidx.compose.foundation.layout.Box
import import androidx.compose.foundation.layout.fillMaxWidth
import import io.github.mangi.eta.ui.components.coui.CouiPreferenceGroup
import import io.github.mangi.eta.ui.components.coui.CouiSectionTitle

/**
 * 自定义系统提示词。
 *
 * 生效内容依旧是单条（输入即保存），额外提供「提示词库」：可以从文件一次导入多条，
 * 列表里点哪条就把那条的内容写进生效字段。运行时读取路径没有变化。
 */
@Composable
@Composable
internal fun CustomSystemPromptScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by CustomizationStore.revision.collectAsState()
    var draft by remember { mutableStateOf(CustomizationStore.systemPrompt()) }
    val presets = remember(revision) { CustomizationStore.promptPresets() }
    val activeId = remember(revision) { CustomizationStore.activePresetId() }

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
        val saved = CustomizationStore.saveCurrentAsPreset(
            context.getString(R.string.prompt_library_saved_name)
        )
        Toast.makeText(
            context,
            context.getString(
                if (saved) R.string.prompt_library_saved_ok else R.string.prompt_library_import_failed
            ),
            Toast.LENGTH_SHORT,
        ).show()
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.custom_prompt_title),
                onBack = onBack,
            ) {
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
                                    title = presetTitle(preset, preset.id == activeId),
                                    summary = presetSummary(preset.content),
                                    onClick = { CustomizationStore.applyPromptPreset(preset.id) },
                                )
                            }
                        }
                    }
                }
                CouiSectionTitle(stringResource(R.string.custom_prompt_summary))
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
                                    title = presetTitle(preset, preset.id == activeId),
                                    summary = presetSummary(preset.content),
                                    onClick = { CustomizationStore.applyPromptPreset(preset.id) },
                                )
                            }
                        }
                    }
                }
                item(key = "custom_prompt") {
                    SmallTitle(text = stringResource(R.string.custom_prompt_summary))
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

private fun presetTitle(preset: PromptPreset, active: Boolean): String =
    if (active) "${preset.name} · 当前使用" else preset.name

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
