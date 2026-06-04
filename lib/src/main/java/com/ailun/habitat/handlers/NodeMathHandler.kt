package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_MATH]：数学运算。
 */
class NodeMathHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val operation = params["operation"]?.toString()?.trim()?.lowercase()
            ?: return NodeResult.failure(node.next, "Missing 'operation' parameter")
        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null } ?: "math_result"

        val a = resolveValue(params["a"], context)
            ?: resolveValue(params["operand1_var"], context)
            ?: return NodeResult.failure(node.next, "Missing operand 'a'")
        val b = resolveValue(params["b"], context)
            ?: resolveValue(params["operand2"], context)
            ?: resolveValue(params["operand2_var"], context)
            ?: return NodeResult.failure(node.next, "Missing operand 'b'")

        val result = when (operation) {
            "add" -> a + b; "subtract" -> a - b
            "multiply" -> a * b
            "divide" -> if (b != 0.0) a / b else Double.NaN
            "modulo" -> if (b != 0.0) a % b else Double.NaN
            "power" -> Math.pow(a, b)
            else -> return NodeResult.failure(node.next, "Unknown operation: $operation")
        }

        context.log("Math $a $operation $b = $result → $outputVar")
        return NodeResult.success(node.next, mapOf(outputVar to result, "math_success" to true))
    }

    private fun resolveValue(raw: Any?, ctx: WorkflowContext): Double? = when (raw) {
        is Number -> raw.toDouble()
        is String -> {
            val fromCtx = ctx.getVariable(raw)
            (fromCtx as? Number)?.toDouble() ?: raw.toDoubleOrNull()
        }
        else -> null
    }
}
