package io.github.mangi.eta.hook.system

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.mangi.eta.config.PowerAssistantTarget
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.core.HookInstallation
import io.github.mangi.eta.core.HookRegistrar
import io.github.mangi.eta.core.HookSupport
import io.github.mangi.eta.core.ModuleConfig
import io.github.mangi.eta.core.ModuleLogger
import io.github.mangi.eta.core.safeLogType
import io.github.libxposed.api.XposedModule

/**
 * 荣耀 MagicOS 长按电源键助理接管。
 *
 * 实测链路（MagicOS 9，services-magic.jar 未混淆）：
 * PhoneWindowManager.launchVoiceAssist(Z)
 *   -> IHwPhoneWindowManagerEx.launchAIPower(Handler, long)
 *   -> HwPhoneWindowManagerEx$3.run()
 *   -> HwPhoneWindowManagerEx.notifyAIPower()
 *   -> startServiceAsUser("com.hihonor.magicvoice.services.STARTASSISTANT", SERVICE_START_MODE=0xb)
 *
 * notifyAIPower 只判断 mHiVoiceKeyType == "power"，完全不读 ASSISTANT role 与
 * voice_interaction_service，所以必须在这个方法落地前替换目标；失败时放行原始逻辑，
 * 保底仍能唤起系统自带的魔法语音，不会出现按了没反应。
 */
internal object HonorPowerHooks {
    private const val DEDUP_WINDOW_MS = ModuleConfig.INTERCEPT_DEDUP_WINDOW_MS
    private const val CONTEXT_FIELD = "mContext"

    @Volatile
    private var lastInterceptUptime = 0L

    fun install(
        module: XposedModule,
        rootLogger: ModuleLogger,
        classLoader: ClassLoader
    ): HookInstallation {
        val hooks = HookRegistrar(module, rootLogger, "HonorPower")
        return hooks.install {
            val pwmExClass = resolveBootClass(classLoader, ModuleConfig.HONOR_PWM_EX_CLASS)
            if (pwmExClass == null) {
                // 非荣耀机型没有这个类，属于正常缺失，不产生告警噪音。
                hooks.skipped(
                    id = "system.honor-ai-power",
                    description = "HwPhoneWindowManagerEx.notifyAIPower",
                    detail = "当前机型无 ${ModuleConfig.HONOR_PWM_EX_CLASS}，跳过荣耀电源键接管"
                )
                return@install
            }

            val notifyMethod = HookSupport.findMethod(
                pwmExClass,
                ModuleConfig.HONOR_NOTIFY_AI_POWER_METHOD
            )
            if (notifyMethod == null) {
                hooks.missing(
                    id = "system.honor-ai-power",
                    description = "HwPhoneWindowManagerEx.notifyAIPower",
                    detail = "未找到 ${ModuleConfig.HONOR_PWM_EX_CLASS}.notifyAIPower()"
                )
                return@install
            }

            hooks.intercept(
                id = "system.honor-ai-power",
                executable = notifyMethod,
                description = "HwPhoneWindowManagerEx.notifyAIPower"
            ) { chain ->
                val target = Prefs.powerAssistantTarget()
                val binding = assistantBindingFor(target)
                if (binding == null) {
                    return@intercept chain.proceed()
                }

                val context = HookSupport.getFieldValue(chain.getThisObject(), CONTEXT_FIELD) as? Context
                if (context == null) {
                    logger.warnThrottled("honor_ai_power_missing_context") {
                        "notifyAIPower 缺少 mContext，回退系统助手"
                    }
                    return@intercept chain.proceed()
                }

                if (!HookSupport.isPackageInstalled(context, binding.packageName)) {
                    logger.warnThrottled("honor_ai_power_${target.persistedValue}_missing") {
                        "notifyAIPower: ${binding.displayName} 未安装，回退系统助手"
                    }
                    return@intercept chain.proceed()
                }

                val now = SystemClock.uptimeMillis()
                if (now - lastInterceptUptime <= DEDUP_WINDOW_MS) {
                    logger.debug { "notifyAIPower: 命中去重窗口，吞掉重复触发" }
                    return@intercept null
                }

                if (AssistantManager.showAssistantSession(
                        context = context,
                        target = target,
                        logger = logger,
                        source = "HonorAIPower"
                    )
                ) {
                    lastInterceptUptime = now
                    logger.debug { "notifyAIPower: 已通过 voiceinteraction 启动 ${binding.displayName}" }
                    return@intercept null
                }

                if (startAssistantActivity(context, binding, logger, "HonorAIPower")) {
                    lastInterceptUptime = now
                    return@intercept null
                }

                logger.warnThrottled("honor_ai_power_${target.persistedValue}_fallback") {
                    "notifyAIPower: ${binding.displayName} 启动失败，回退系统助手"
                }
                chain.proceed()
            }
        }
    }

    /** HwPhoneWindowManagerEx 由 boot class loader 提供，模块 classLoader 取不到时逐个兜底。 */
    private fun resolveBootClass(classLoader: ClassLoader, className: String): Class<*>? {
        HookSupport.findClassOrNull(classLoader, className)?.let { return it }
        return runCatching { Class.forName(className, false, ClassLoader.getSystemClassLoader()) }
            .getOrNull()
            ?: runCatching { Class.forName(className) }.getOrNull()
    }

    private fun startAssistantActivity(
        context: Context,
        binding: AssistantBinding,
        logger: ModuleLogger,
        source: String
    ): Boolean {
        val intent = Intent(Intent.ACTION_ASSIST).apply {
            setPackage(binding.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolves = runCatching { HookSupport.resolvesActivity(context, intent) }
            .getOrElse { throwable ->
                logger.warnThrottled("${source}_resolve_failed") {
                    "$source: 查询 ${binding.displayName} ASSIST 入口失败，type=${throwable.safeLogType()}"
                }
                false
            }
        if (!resolves) {
            logger.warnThrottled("${source}_assist_missing") {
                "$source: ${binding.displayName} 未暴露 ACTION_ASSIST"
            }
            return false
        }
        return runCatching {
            context.startActivity(intent)
            logger.debug { "$source: 已通过 ACTION_ASSIST 启动 ${binding.displayName}" }
            true
        }.getOrElse { throwable ->
            logger.warnThrottled("${source}_assist_failed") {
                "$source: ACTION_ASSIST 启动失败，回退系统助手，type=${throwable.safeLogType()}"
            }
            false
        }
    }
}
