package io.github.mangi.eta.agent.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * ask_user：模型主动向用户提问并等待选择或自由输入。
 * 工具执行线程在 AgentQuestionBus 上挂起，聊天界面渲染提问卡；
 * 用户提交后答案作为工具结果返回给模型继续推进。
 */
internal object AgentAskToolCatalog {
    const val NAME = "ask_user"

    fun appendTo(tools: JSONArray) {
        tools.put(
            AgentToolSchema.function(
                name = NAME,
                description = "向用户提出问题并等待回答。适用于：关键决策存在多个合理选项、" +
                    "缺少继续执行所必需的信息、或用户明确要求确认时。" +
                    "用户可以在选项中选择（单选/多选）或自由输入；也可以跳过。" +
                    "不要用于闲聊，一次只问一个问题。",
                parameters = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put(
                                "question",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "要问的问题，简洁明确，包含必要的上下文"),
                            )
                            .put(
                                "title",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "卡片标题，例如「选择部署方式」，省略时用「需要你的输入」"),
                            )
                            .put(
                                "options",
                                JSONObject()
                                    .put("type", "array")
                                    .put("items", JSONObject().put("type", "string"))
                                    .put("description", "候选项（2-6 个）。省略时用户自由输入"),
                            )
                            .put(
                                "multi_select",
                                JSONObject()
                                    .put("type", "boolean")
                                    .put("description", "true 时允许多选；默认单选"),
                            ),
                    )
                    .put("required", JSONArray().put("question")),
            ),
        )
    }
}
