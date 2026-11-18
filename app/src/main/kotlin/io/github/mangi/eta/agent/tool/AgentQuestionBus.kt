package io.github.mangi.eta.agent.tool

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 会话内卡片（提问卡 / 工具审批卡）的挂起与应答总线。
 *
 * 工具执行线程在 [ask] 上挂起，UI 收集 [pending] 渲染卡片，用户提交后经 [submit]
 * 唤醒执行线程。多个会话的卡可以同时挂起，互不干扰；run 终止时调 [cancelAll]
 * 把所有挂起点按取消处理。
 */
internal object AgentQuestionBus {
    enum class Kind { ASK_USER, TOOL_APPROVAL }

    data class PendingQuestion(
        val id: String,
        val kind: Kind,
        val title: String,
        val question: String,
        val options: List<String> = emptyList(),
        val multiSelect: Boolean = false,
        /** 工具审批卡专用：待执行的工具名与参数摘要。 */
        val toolName: String? = null,
        val toolArgsSummary: String? = null,
        /** 审批卡专用：本次会话内对同名工具放行的选项。 */
        val approvalSessionOption: Boolean = false,
    )

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    private val answers = HashMap<String, String>()
    private val cancelledIds = mutableSetOf<String>()

    private val _pending = MutableStateFlow<List<PendingQuestion>>(emptyList())
    val pending: StateFlow<List<PendingQuestion>> = _pending

    /**
     * 发布问题并挂起直到用户应答或 run 取消。
     * 返回用户的回答文本（单选/审批为选项文本，多选为 JSON 数组，自由输入为原文）。
     */
    fun ask(question: PendingQuestion): String {
        lock.withLock {
            answers.remove(question.id)
            cancelledIds.remove(question.id)
        }
        _pending.value = _pending.value + question
        try {
            lock.withLock {
                while (true) {
                    answers[question.id]?.let { return it }
                    if (question.id in cancelledIds) throw InterruptedException()
                    condition.await()
                }
                @Suppress("UNREACHABLE_CODE")
                throw IllegalStateException()
            }
        } finally {
            _pending.value = _pending.value.filterNot { it.id == question.id }
            lock.withLock {
                answers.remove(question.id)
                cancelledIds.remove(question.id)
            }
        }
    }

    /** UI 提交回答：单选/审批传选项文本，多选传 JSON 数组，自由输入传原文；空串表示跳过。 */
    fun submit(id: String, answer: String) {
        lock.withLock {
            answers[id] = answer
            condition.signalAll()
        }
    }

    /** run 终止：唤醒全部挂起点并按取消处理。 */
    fun cancelAll() {
        lock.withLock {
            _pending.value.forEach { cancelledIds.add(it.id) }
            condition.signalAll()
        }
        _pending.value = emptyList()
    }
}
