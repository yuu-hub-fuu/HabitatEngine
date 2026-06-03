package com.ailun.habitat.handlers

import com.ailun.habitat.ExpressionEngine
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [CONDITION_SWITCH]：根据 expression 选择 branches["true"] 或 branches["false"]。
 */
class SwitchNodeHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val expr = node.params?.get("expression")?.toString()?.trim().orEmpty()
        val result = ExpressionEngine.eval(expr, context)
        context.log("Switch: ${result.explanation}")
        val next = node.branches?.get(if (result.value) "true" else "false")
        return NodeResult.success(next)
    }
}
