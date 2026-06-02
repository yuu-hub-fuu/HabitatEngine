package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlin.random.Random

/**
 * [ACTION_RANDOM]：生成随机数。
 * params：
 * - `min`（可选）：最小值（含），默认 0
 * - `max`（可选）：最大值（含），默认 100
 * - `output_var`（可选）：输出变量名，默认 "random_value"
 * - `type`（可选）："int"（默认）/ "float" / "boolean"
 * 输出：random_value
 */
class NodeRandomHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val min = (node.params?.get("min") as? Number)?.toInt() ?: 0
        val max = (node.params?.get("max") as? Number)?.toInt() ?: 100
        val type = node.params?.get("type")?.toString()?.lowercase() ?: "int"
        val outputVar = node.params?.get("output_var")?.toString()?.ifEmpty { null } ?: "random_value"

        when (type) {
            "float" -> context.variables[outputVar] = Random.nextDouble(min.toDouble(), max.toDouble() + 1.0)
            "boolean" -> context.variables[outputVar] = Random.nextBoolean()
            else -> context.variables[outputVar] = Random.nextInt(min, max + 1)
        }
        context.variables["random_success"] = true
        context.log("Random type=$type min=$min max=$max → ${context.variables[outputVar]}")
        return node.next
    }
}
