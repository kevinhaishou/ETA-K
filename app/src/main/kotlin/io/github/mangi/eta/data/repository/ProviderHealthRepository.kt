package io.github.mangi.eta.data.repository

import io.github.mangi.eta.agent.model.AgentModelFailure
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.agent.model.ProviderClientFactory
import io.github.mangi.eta.agent.model.ProviderRequest
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ProviderSetting
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 单个供应商的连通性结论。 */
internal enum class ProviderHealthState { Unknown, Testing, Reachable, Unreachable }

internal data class ProviderHealth(
    val state: ProviderHealthState = ProviderHealthState.Unknown,
    val modelCount: Int = 0,
    val detail: String? = null,
    val checkedAt: Long = 0L,
    /** 探测到的远端模型，供「补全缺失模型」复用，避免二次请求。 */
    val models: List<Model> = emptyList(),
)

/**
 * 批量连通性探测：复用详情页「测试连接」的只读模型目录拉取，不写任何供应商数据。
 *
 * 结论只放内存：网络状况随时会变，把某次探测当成持久事实反而误导。
 */
internal object ProviderHealthRepository {
    private const val MAX_PARALLEL = 4

    private val _health = MutableStateFlow<Map<String, ProviderHealth>>(emptyMap())
    val health: StateFlow<Map<String, ProviderHealth>> = _health.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /** 并发探测全部已启用的供应商；重复调用会取消上一轮。 */
    fun testAll(providers: List<ProviderSetting>) {
        job?.cancel()
        val targets = providers.filter { it.isEnabled }
        if (targets.isEmpty()) {
            _health.value = emptyMap()
            return
        }
        _health.value = targets.associate { it.id to ProviderHealth(state = ProviderHealthState.Testing) }
        job = scope.launch {
            val gate = Semaphore(MAX_PARALLEL)
            val results = targets.map { provider ->
                async {
                    gate.withPermit { provider.id to probe(provider) }
                }
            }.awaitAll()
            _health.value = results.toMap()
        }
    }

    private suspend fun probe(provider: ProviderSetting): ProviderHealth {
        val checkedAt = System.currentTimeMillis()
        // 第一步：拉模型目录，先确认地址与目录鉴权可达，并拿到一个真实模型名。
        val models = RemoteModelFetcher.fetch(provider).getOrElse { throwable ->
            return ProviderHealth(
                state = ProviderHealthState.Unreachable,
                detail = reason(throwable),
                checkedAt = checkedAt,
            )
        }
        if (models.isEmpty()) {
            return ProviderHealth(
                state = ProviderHealthState.Unreachable,
                detail = "模型列表为空",
                checkedAt = checkedAt,
            )
        }
        // 第二步：真实对话探测。models 接口不需要 key 或权限较宽的供应商很多，
        // 拉到列表不等于能对话，这里发一条极小的消息验证真实可用性。
        val config = RuntimeConfigRepository.buildRuntimeConfig(provider, models.first())
        return try {
            val client = ProviderClientFactory.getClient(config)
            val messages = JSONArray()
                .put(JSONObject().put("role", "user").put("content", "ping"))
            client.complete(ProviderRequest(config, messages, JSONArray()), AgentRunController())
            ProviderHealth(
                state = ProviderHealthState.Reachable,
                modelCount = models.size,
                checkedAt = checkedAt,
                models = models,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            ProviderHealth(
                state = ProviderHealthState.Unreachable,
                detail = reason(throwable),
                checkedAt = checkedAt,
            )
        }
    }

    /** 失败原因优先取 AgentModelFailure 的友好文案，其余退化为异常消息或类名。 */
    private fun reason(throwable: Throwable): String =
        (throwable as? AgentModelFailure)?.message?.takeIf { it.isNotBlank() }
            ?: throwable.message?.takeIf { it.isNotBlank() }
            ?: throwable.javaClass.simpleName

    fun clear() {
        job?.cancel()
        job = null
        _health.value = emptyMap()
    }
}
