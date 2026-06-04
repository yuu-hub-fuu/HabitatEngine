package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_TEXT_OPERATION]：文本操作。
 */
class NodeTextOperationHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val op = (params["action"]?.toString() ?: params["operation"]?.toString())
            ?.trim()?.lowercase()
            ?: return NodeResult.failure(node.next, "Missing 'action'/'operation' parameter")

        val inputKey = (params["input"]?.toString() ?: params["source_var"]?.toString())?.trim().orEmpty()
        val sourceValue = context.getVariable(inputKey)?.toString() ?: inputKey
        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null } ?: "text_output"

        val result = when (op) {
            "replace" -> {
                val old = (params["old"]?.toString() ?: params["search"]?.toString()).orEmpty()
                val new = (params["new"]?.toString() ?: params["replace_with"]?.toString()).orEmpty()
                sourceValue.replace(old, new)
            }
            "substring" -> {
                val start = (params["start"] as? Number)?.toInt() ?: 0
                val end = (params["end"] as? Number)?.toInt() ?: sourceValue.length
                sourceValue.substring(maxOf(0, start).coerceAtMost(sourceValue.length),
                    end.coerceAtMost(sourceValue.length))
            }
            "split" -> {
                val delimiter = params["delimiter"]?.toString() ?: ","
                sourceValue.split(delimiter).joinToString("\n")
            }
            "uppercase" -> sourceValue.uppercase()
            "lowercase" -> sourceValue.lowercase()
            "trim" -> sourceValue.trim()
            "append" -> sourceValue + (params["text"]?.toString().orEmpty())
            "prepend" -> (params["text"]?.toString().orEmpty()) + sourceValue
            else -> return NodeResult.failure(node.next, "Unknown operation: $op")
        }

        context.log("TextOp $op '$inputKey' → '$result' (→ $outputVar)")
        return NodeResult.success(node.next, mapOf(outputVar to result, "text_op_success" to true))
    }
}
