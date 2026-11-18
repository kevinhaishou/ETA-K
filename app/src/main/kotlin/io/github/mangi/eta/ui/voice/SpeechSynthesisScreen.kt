package io.github.mangi.eta.ui.voice

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.layout.Divider
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.SystemTtsProbe
import io.github.mangi.eta.agent.voice.validateSpeechSettings
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SpeechSynthesisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SpeechSettingsStore(context, scope) }
    SpeechLifecycle { store.stop() }
    val settings = store.settings
    val ttsLabels = TtsProvider.entries.map { it.label() }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.speech_synthesis_title),
                onBack = onBack,
            ) {
                if (!store.loaded) {
                    SpeechLoadingState(store.message)
                    return@CouiScaffoldPage
                }
                SpeechGroupCard(title = stringResource(R.string.speech_group_service)) {
                    CouiDropdownPreference(
                        title = stringResource(R.string.speech_tts_service),
                        items = ttsLabels,
                        selectedIndex = settings.tts.ordinal,
                        onSelectedIndexChange = { index ->
                            TtsProvider.entries.getOrNull(index)?.let { store.edit(settings.copy(tts = it)) }
                        },
                    )
                }
                if (settings.tts != TtsProvider.NONE) {
                    SpeechConnectionSection(store, synthesis = true)
                    if (settings.tts == TtsProvider.ANDROID) {
                        SystemTtsSection(store)
                    } else if (settings.tts != TtsProvider.CLONE) {
                        SpeechVoiceSection(store)
                    }
                    // 克隆是独立引擎：系统朗读与克隆都要能配它，单独一处入口。
                    if (settings.tts == TtsProvider.ANDROID || settings.tts == TtsProvider.CLONE) {
                        CloneVoiceSection()
                    }
                    SpeechPreviewSection(store)
                }
                SpeechSaveBlock(store) {
                    if (store.settings.tts != TtsProvider.NONE) {
                        validateSpeechSettings(
                            store.settings,
                            store.credentials,
                            synthesis = true,
                            hasProviderDeepSeekKey = store.hasProviderDeepSeekKey,
                        )
                    }
                }
                CouiPageBottomSpacer()
            }
        }
        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.speech_synthesis_title),
                onBack = onBack,
                modifier = Modifier.imePadding(),
            ) {
                if (!store.loaded) {
                    item(key = "loading") { SpeechLoadingState(store.message) }
                    return@MiuixScaffoldPage
                }
                item(key = "service") {
                    SpeechGroupCard(title = stringResource(R.string.speech_group_service)) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.speech_tts_service),
                            items = ttsLabels,
                            selectedIndex = settings.tts.ordinal,
                            onSelectedIndexChange = { index ->
                                TtsProvider.entries.getOrNull(index)?.let { store.edit(settings.copy(tts = it)) }
                            },
                        )
                    }
                }
                if (settings.tts != TtsProvider.NONE) {
                    item(key = "connection") {
                        SpeechConnectionSection(store, synthesis = true)
                    }
                    item(key = "voice") {
                        if (settings.tts == TtsProvider.ANDROID) {
                            SystemTtsSection(store)
                        } else if (settings.tts != TtsProvider.CLONE) {
                            SpeechVoiceSection(store)
                        }
                        if (settings.tts == TtsProvider.ANDROID ||
                            settings.tts == TtsProvider.CLONE
                        ) {
                            CloneVoiceSection()
                        }
                    }
                    item(key = "preview") {
                        SpeechPreviewSection(store)
                    }
                }
                item(key = "save") {
                    SpeechSaveBlock(store) {
                        if (store.settings.tts != TtsProvider.NONE) {
                            validateSpeechSettings(
                                store.settings,
                                store.credentials,
                                synthesis = true,
                                hasProviderDeepSeekKey = store.hasProviderDeepSeekKey,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechPreviewSection(store: SpeechSettingsStore) {
    val playback by store.playback.state.collectAsState()
    val playing = playback.messageId != null
    val defaultText = stringResource(R.string.speech_preview_default_text)
    var text by rememberSaveable { mutableStateOf(defaultText) }
    val title = stringResource(if (playing) R.string.speech_preview_stop else R.string.speech_preview_voice)
    val summary = playback.error ?: if (playback.loading) stringResource(R.string.speech_preview_loading) else null
    // 进入播报页自动试听一次（仅本机引擎）：用于确认调用链是否打通
    LaunchedEffect(store.settings.tts) {
        if (store.settings.tts == TtsProvider.ANDROID) {
            android.util.Log.w("EtaSystemTts", "auto preview enter tts=ANDROID")
            store.playback.speak("preview-auto", text, store.settings, store.credentials)
        }
    }

    val previewContext = LocalContext.current
    val onPreviewClick: () -> Unit = {
        if (store.settings.tts == TtsProvider.ANDROID) {
            // 本机引擎直接播报，绕开播报控制器与前台服务
            SystemTtsProbe.speak(
                context = previewContext,
                text = text,
                onResult = { message ->
                    Toast.makeText(previewContext, message, Toast.LENGTH_SHORT).show()
                },
            )
        } else {
            store.playback.stop()
            store.playback.speak(
                "preview",
                text,
                store.settings,
                store.credentials,
            )
        }
    }

    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_preview_voice)) {
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(R.string.speech_preview_text),
                    value = text,
                    singleLine = false,
                    enabled = !store.saving,
                    onChange = {
                        if (it.length <= 500) {
                            store.playback.stop()
                            text = it
                        }
                    },
                )
            }
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    val dialogColors = CouiDialogDefaults.themeColors()
                    Divider()
                    CouiPreference(
                        title = title,
                        summary = summary,
                        summaryColor = if (playback.error != null) dialogColors.destructive else Color.Unspecified,
                        enabled = text.isNotBlank(),
                        showChevron = false,
                        onClick = onPreviewClick,
                    )
                }
                AppearanceUiStyle.MIUIX -> {
                    BasicComponent(
                        title = title,
                        summary = summary,
                        summaryColor = if (playback.error != null) {
                            BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.error)
                        } else {
                            BasicComponentDefaults.summaryColor()
                        },
                        enabled = text.isNotBlank(),
                        onClick = onPreviewClick,
                    )
                }
            }
        }
        SpeechNote(stringResource(R.string.speech_preview_note))
    }
}
