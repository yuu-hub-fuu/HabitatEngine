package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.expression.ExpressionEngine

/**
 * [CONDITION_ADVANCED_SWITCH]：高级条件分支。
 * 支持 expression 和 var+operator+value 两种格式。
 */
class NodeAdvancedSwitchHandler : INodeHandler {
    private val exprEngine = ExpressionEngine()

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val expr = node.params?.get("expression")?.toString()?.trim()
            ?: buildExpression(node.params ?: emptyMap())

        val result = exprEngine.evaluate(expr, context)
        context.log("AdvancedSwitch: ${result.explanation}")
        return NodeResult.success(node.branches?.get(if (result.booleanResult) "true" else "false"))
    }

    private fun buildExpression(params: Map<String, Any?>): String {
        val v = params["var"]?.toString()?.trim() ?: return ""
        val op = params["operator"]?.toString()?.trim() ?: "=="
        val valStr = params["value"]?.toString()?.trim() ?: ""
        return "$v $op $valStr"
    }
}
