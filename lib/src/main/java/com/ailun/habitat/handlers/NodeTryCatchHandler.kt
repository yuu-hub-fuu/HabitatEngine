package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.RuntimeVars
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
        val hasError = context.getVariable(RuntimeVars.LAST_ERROR) as? Boolean ?: false
        val errorMsg = context.getVariable(RuntimeVars.LAST_ERROR_MSG)?.toString().orEmpty()
        val catchVar = node.params?.get("catch_var")?.toString() ?: "exception_msg"

        if (hasError) {
            context.putVariable(catchVar, errorMsg)
        }

        context.putVariable(RuntimeVars.LAST_ERROR, false)
        context.putVariable(RuntimeVars.LAST_ERROR_MSG, "")

        val key = if (hasError) "error" else "success"
        context.log("TryCatch hasError=$hasError → branch=$key")
        return NodeResult.success(node.branches?.get(key))
    }
}
