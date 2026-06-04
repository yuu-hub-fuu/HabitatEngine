package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.RuntimeVars
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_CONFIRM] — 确认门。
 * 将执行流程引到 branches["pending"]，同时设置确认标记供上层 UI 读取。
 *
 * Used by PlanCompiler to auto-insert before high-risk nodes.
 * The actual confirmation UI is handled by [ConfirmationManager] via [IConfirmationProvider].
 */
class NodeConfirmHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val message = node.params?.get("message")?.toString() ?: "需要确认后继续"
        context.log("CONFIRM REQUIRED: $message")
        context.putVariable(RuntimeVars.CONFIRM_REQUIRED, true)
        context.putVariable(RuntimeVars.CONFIRM_MESSAGE, message)

        // If confirmation was pre-approved (token from compiler/UI), go to "approved"
        val preToken = node.params?.get("_confirm_token")?.toString()
        if (preToken != null) {
            val approved = context.getVariable("_confirm_token_approved") as? String
            if (approved == preToken) {
                context.log("CONFIRM: pre-approved via token")
                return NodeResult.success(node.next)
            }
        }

        // Otherwise, go to "pending" branch for UI to handle
        return NodeResult.success(node.branches?.get("pending"))
    }
}
