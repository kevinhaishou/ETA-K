package io.github.mangi.eta.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.customization.CustomizationStore
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.screens.characters.CharacterTextField
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * 自定义系统提示词：输入即保存，追加在服务商提示词之后、默认约束之前生效。
 */
@Composable
internal fun CustomSystemPromptScreen(onBack: () -> Unit) {
    var draft by remember { mutableStateOf(CustomizationStore.systemPrompt()) }

    val onChange: (String) -> Unit = { value ->
        draft = value
        CustomizationStore.setSystemPrompt(value)
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.custom_prompt_title),
                onBack = onBack,
            ) {
                CharacterTextField(
                    label = stringResource(R.string.custom_prompt_hint),
                    value = draft,
                    onChange = onChange,
                    enabled = true,
                    minLines = 8,
                )
                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.custom_prompt_title),
                onBack = onBack,
            ) {
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