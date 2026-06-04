package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlin.random.Random

/**
 * [ACTION_RANDOM]：生成随机数。
 */
class NodeRandomHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")
        val min = (params["min"] as? Number)?.toInt() ?: 0
        val max = (params["max"] as? Number)?.toInt() ?: 100
        val type = params["type"]?.toString()?.lowercase() ?: "int"
        val outputVar = params["output_var"]?.toString()?.ifEmpty { null } ?: "random_value"

        if (max < min) return NodeResult.failure(node.next, "max ($max) < min ($min)")

        val value = when (type) {
            "float" -> Random.nextDouble(min.toDouble(), max.toDouble() + 1.0)
            "boolean" -> Random.nextBoolean()
            else -> Random.nextInt(min, max + 1)
        }
        context.log("Random type=$type min=$min max=$max → $value")
        return NodeResult.success(node.next, mapOf(
            outputVar to value, "random_success" to true,
        ))
    }
}
