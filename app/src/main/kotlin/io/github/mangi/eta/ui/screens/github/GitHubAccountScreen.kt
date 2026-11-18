package io.github.mangi.eta.ui.screens.github

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.github.GitHubAccountManager
import io.github.mangi.eta.agent.github.GitHubAccountState
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * GitHub 账号：一键走 OAuth 设备码连接，token 用 Android Keystore 加密只存本机。
 * 连接后会注册 GitHub 官方远程 MCP，并可选把凭据同步给 Linux 环境的 git。
 */
@Composable
internal fun GitHubAccountScreen(onBack: () -> Unit) {
    val state by GitHubAccountManager.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var syncEnabled by remember { mutableStateOf(GitHubAccountManager.syncToTerminalEnabled()) }

    LaunchedEffect(Unit) { GitHubAccountManager.syncIfConfigured() }

    MiuixScaffoldPage(title = "GitHub 账号", onBack = onBack) {
        item(key = "intro") {
            Text(
                text = "一键连接，之后可直接 clone/push、提 issue/PR",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 12.dp),
            )
        }

        item(key = "account") {
            PreferenceCard(title = "账号") {
                when (val current = state) {
                    GitHubAccountState.Disconnected -> ArrowPreference(
                        title = "连接 GitHub",
                        summary = "点一下走设备码授权，不需要手动建 token",
                        onClick = { GitHubAccountManager.connect() },
                    )

                    GitHubAccountState.Connecting -> ArrowPreference(
                        title = "正在申请设备码…",
                        summary = "马上会给出 8 位代码",
                        onClick = {},
                    )

                    is GitHubAccountState.Waiting -> {
                        ArrowPreference(
                            title = "在浏览器输入：${current.userCode}",
                            summary = "打开 ${current.verificationUri}，输入这串代码并点 Authorize",
                            onClick = {
                                clipboard.setText(AnnotatedString(current.userCode))
                                Toast.makeText(context, "已复制 ${current.userCode}", Toast.LENGTH_SHORT).show()
                            },
                        )
                        ArrowPreference(
                            title = "打开授权页面",
                            summary = "会先把代码复制到剪贴板，粘贴即用",
                            onClick = {
                                clipboard.setText(AnnotatedString(current.userCode))
                                Toast.makeText(context, "已复制 ${current.userCode}", Toast.LENGTH_SHORT).show()
                                runCatching { uriHandler.openUri(current.verificationUri) }
                            },
                        )
                        ArrowPreference(
                            title = "取消",
                            summary = "停止等待授权",
                            onClick = { GitHubAccountManager.cancel() },
                        )
                    }

                    is GitHubAccountState.Failed -> ArrowPreference(
                        title = "连接失败",
                        summary = current.message,
                        onClick = { GitHubAccountManager.connect() },
                    )

                    is GitHubAccountState.Connected -> ArrowPreference(
                        title = "已连接 @${current.login}",
                        summary = "权限：${current.scopes}",
                        onClick = {},
                    )
                }
            }
        }

        val connected = state as? GitHubAccountState.Connected
        if (connected != null) {
            item(key = "terminal") {
                PreferenceCard(title = "终端") {
                    SwitchPreference(
                        title = "同步给终端 git",
                        summary = terminalSummary(connected),
                        checked = syncEnabled,
                        onCheckedChange = { enabled ->
                            syncEnabled = enabled
                            GitHubAccountManager.setSyncToTerminal(enabled)
                        },
                    )
                    ArrowPreference(
                        title = "断开连接",
                        summary = "清除本机 token、自动注册的 MCP 服务器与终端凭据",
                        onClick = { GitHubAccountManager.disconnect() },
                    )
                }
            }
        }

        item(key = "about") {
            PreferenceCard(title = "说明") {
                ArrowPreference(
                    title = "token 存在哪",
                    summary = "用 Android Keystore 加密后只存在本机；开启终端同步时会写入 Linux 环境的 git 凭据文件（仅 root 可读）。",
                    onClick = {},
                )
                ArrowPreference(
                    title = "怎么撤销",
                    summary = "GitHub → Settings → Applications → Authorized OAuth Apps，随时可以撤销；这里断开只是清本机凭据。",
                    onClick = {},
                )
                ArrowPreference(
                    title = "连接后会得到什么",
                    summary = "自动注册一条 GitHub 官方远程 MCP 服务器（issue、PR、代码搜索、合并等），并让终端里的 git clone/push 走同一份凭据。",
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun PreferenceCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(title)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        ) {
            Column { content() }
        }
    }
}

private fun terminalSummary(state: GitHubAccountState.Connected): String = when {
    state.syncError != null -> "写入失败：${state.syncError}"

    state.syncedTerminals.isNotEmpty() && state.pendingTerminals.isEmpty() ->
        "已写入：${state.syncedTerminals.joinToString("、")}"

    state.syncedTerminals.isNotEmpty() ->
        "已写入：${state.syncedTerminals.joinToString("、")}；未就绪：${state.pendingTerminals.joinToString("、")}"

    state.pendingTerminals.isNotEmpty() ->
        "等 Linux 环境就绪后再同步（${state.pendingTerminals.joinToString("、")}）"

    else -> "开启后会把凭据写进 Linux 环境"
}
