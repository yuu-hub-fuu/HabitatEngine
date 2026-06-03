package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_TRY_CATCH] — 错误路由器。
 *
 * 读取上一步是否已经产生错误标记（_last_error），如果上一步异常：
 * - 将错误信息写入 catch_var
 * - 路由到 branches["error"]
 * 否则路由到 branches["success"]。
 */
class NodeTryCatchHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val hasError = context.getVariable("_last_error") as? Boolean ?: false
        val errorMsg = context.getVariable("_last_error_msg")?.toString().orEmpty()
        val catchVar = node.params?.get("catch_var")?.toString() ?: "exception_msg"

        if (hasError) {
            context.putVariable(catchVar, errorMsg)
        }

        context.putVariable("_last_error", false)
        context.putVariable("_last_error_msg", "")

        val key = if (hasError) "error" else "success"
        context.log("TryCatch hasError=$hasError → branch=$key")
        return NodeResult.success(node.branches?.get(key))
    }
}
