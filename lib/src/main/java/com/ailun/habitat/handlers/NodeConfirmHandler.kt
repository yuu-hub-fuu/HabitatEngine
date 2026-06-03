package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_CONFIRM] — 确认门。
 * 将执行流程引到 branches["pending"]，同时设置确认标记供上层 UI 读取。
 */
class NodeConfirmHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val message = node.params?.get("message")?.toString() ?: "需要确认后继续"
        context.log("CONFIRM REQUIRED: $message")
        context.putVariable("_confirm_required", true)
        context.putVariable("_confirm_message", message)
        return NodeResult.success(node.branches?.get("pending"))
    }
}
