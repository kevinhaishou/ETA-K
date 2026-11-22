package io.github.mangi.eta.ui.screens.backup

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.core.safeStackTrace
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.repository.EtaBackupSummary
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiPreferenceGroup
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.currentGlassOverlayBackdrop
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun DataBackupScreen(
    context: Context,
    onBack: () -> Unit,
    onExport: suspend (OutputStream, (String) -> Unit) -> EtaBackupSummary,
    onImport: suspend (InputStream, (String) -> Unit) -> EtaBackupSummary,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // 导出/导入的阶段文案：数据量大时（数万条消息）逐条序列化要几十秒，
    // 只显示"处理中"会让人以为是卡死。
    var phaseText by remember { mutableStateOf("") }
    val onPhase: (String) -> Unit = { raw -> phaseText = backupPhaseLabel(raw) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    fun showFailure(throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        AndroidAgentLogger.error("Agent backup UI failed: type=${throwable.safeLogType()}\n${throwable.safeStackTrace()}")
        Toast.makeText(
            context,
            throwable.message ?: context.getString(R.string.data_backup_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            phaseText = ""
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error(context.getString(R.string.data_backup_file_open_failed))
                val summary = output.use { onExport(it, onPhase) }
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.data_backup_exported,
                        summary.conversationCount,
                        summary.providerCount,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (throwable: Throwable) {
                showFailure(throwable)
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportDialog = true
        }
    }

    val confirmImportText = if (busy) {
        stringResource(R.string.data_backup_working)
    } else {
        stringResource(R.string.action_import)
    }
    val onDismissImport: () -> Unit = {
        if (!busy) {
            showImportDialog = false
            pendingImportUri = null
        }
    }
    val onConfirmImport: () -> Unit = {
        val uri = pendingImportUri
        if (uri != null) {
            showImportDialog = false
            scope.launch {
                busy = true
                try {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error(context.getString(R.string.data_backup_file_open_failed))
                    val summary = input.use { onImport(it, onPhase) }
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.data_backup_imported,
                            summary.conversationCount,
                            summary.providerCount,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                } catch (throwable: Throwable) {
                    showFailure(throwable)
                } finally {
                    pendingImportUri = null
                    busy = false
                }
            }
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.data_backup_title),
                onBack = onBack,
                overlay = {
                    CouiConfirmDialog(
                        visible = showImportDialog,
                        title = stringResource(R.string.data_backup_import_confirm_title),
                        message = stringResource(R.string.data_backup_import_confirm_summary),
                        confirmText = confirmImportText,
                        confirmEnabled = !busy,
                        cancelEnabled = !busy,
                        destructive = true,
                        onConfirm = onConfirmImport,
                        onDismissRequest = onDismissImport,
                    )
                },
            ) {
                CouiPreferenceGroup {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.data_backup_warning_title),
                            summary = stringResource(R.string.data_backup_warning_summary),
                            showChevron = false,
                        )
                    }
                }
                CouiPreferenceGroup(title = stringResource(R.string.data_backup_actions)) {
                    item {
                        CouiPreference(
                            title = stringResource(R.string.data_backup_export),
                            summary = if (busy) {
                                phaseText.ifBlank { stringResource(R.string.data_backup_working) }
                            } else {
                                stringResource(R.string.data_backup_export_summary)
                            },
                            enabled = !busy,
                            trailing = if (busy) {
                                { InfiniteProgressIndicator(size = 20.dp) }
                            } else {
                                null
                            },
                            onClick = { exportLauncher.launch(defaultBackupFileName()) },
                        )
                    }
                    item {
                        CouiPreference(
                            title = stringResource(R.string.data_backup_import),
                            summary = stringResource(R.string.data_backup_import_summary),
                            enabled = !busy,
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                        )
                    }
                }
                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.data_backup_title),
                onBack = onBack,
                overlay = {
                    GlassDialog(
                        show = showImportDialog,
                        backdrop = currentGlassOverlayBackdrop(),
                        title = stringResource(R.string.data_backup_import_confirm_title),
                        summary = stringResource(R.string.data_backup_import_confirm_summary),
                        onDismissRequest = onDismissImport,
                    ) {
                        MiuixDialogActions(
                            confirmText = confirmImportText,
                            destructive = true,
                            cancelEnabled = !busy,
                            confirmEnabled = !busy,
                            onCancel = onDismissImport,
                            onConfirm = onConfirmImport,
                        )
                    }
                },
            ) {
                item(key = "warning") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp, bottom = 12.dp),
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.data_backup_warning_title),
                            summary = stringResource(R.string.data_backup_warning_summary),
                        )
                    }
                }
                item(key = "actions") {
                    SmallTitle(stringResource(R.string.data_backup_actions))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.data_backup_export),
                            summary = if (busy) {
                                stringResource(R.string.data_backup_working)
                            } else {
                                stringResource(R.string.data_backup_export_summary)
                            },
                            enabled = !busy,
                            endActions = {
                                if (busy) {
                                    InfiniteProgressIndicator(size = 20.dp)
                                }
                            },
                            onClick = {
                                exportLauncher.launch(defaultBackupFileName())
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.data_backup_import),
                            summary = stringResource(R.string.data_backup_import_summary),
                            enabled = !busy,
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun defaultBackupFileName(): String =
    "Eta-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.eta-backup.json"

/** 把导出/导入阶段名映射成可读文案：数万条消息时用户能看到进度在走。 */
private fun backupPhaseLabel(raw: String): String = when (raw) {
    "prepare_export", "read_header" -> "\u6b63\u5728\u51c6\u5907\u2026"
    "stage_export" -> "\u6b63\u5728\u5199\u5165\u6570\u636e\u2026"
    "copy_export" -> "\u6b63\u5728\u5b8c\u6210\u5199\u5165\u2026"
    "export_conversations" -> "\u6b63\u5728\u5bfc\u51fa\u5bf9\u8bdd\u5217\u8868\u2026"
    "export_messages" -> "\u6b63\u5728\u5bfc\u51fa\u6d88\u606f\uff08\u6570\u4e07\u6761\u65f6\u8f83\u4e45\uff09\u2026"
    "export_checkpoints" -> "\u6b63\u5728\u5bfc\u51fa\u4e0a\u4e0b\u6587\u5feb\u7167\u2026"
    "export_roleplay" -> "\u6b63\u5728\u5bfc\u51fa\u89d2\u8272\u4e0e\u5934\u50cf\u2026"
    "read_conversations" -> "\u6b63\u5728\u8bfb\u53d6\u5bf9\u8bdd\u5217\u8868\u2026"
    "read_messages" -> "\u6b63\u5728\u8bfb\u53d6\u6d88\u606f\uff08\u6570\u4e07\u6761\u65f6\u8f83\u4e45\uff09\u2026"
    "read_checkpoints" -> "\u6b63\u5728\u8bfb\u53d6\u4e0a\u4e0b\u6587\u5feb\u7167\u2026"
    "read_roleplay" -> "\u6b63\u5728\u8bfb\u53d6\u89d2\u8272\u4e0e\u5934\u50cf\u2026"
    "apply_import" -> "\u6b63\u5728\u5199\u5165\u6570\u636e\u5e93\u2026"
    else -> if (raw.startsWith("read_")) "\u6b63\u5728\u8bfb\u53d6\u6570\u636e\u2026" else "\u5904\u7406\u4e2d\u2026"
}
