package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.expression.ExpressionEngine

/**
 * [ACTION_LOOP]：简单循环控制（计数或条件）。
 */
class NodeLoopHandler : INodeHandler {
    private val exprEngine = ExpressionEngine()

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.success(node.next)
        val mode = params["mode"]?.toString()?.trim()?.lowercase() ?: "count"

        val nextId = when (mode) {
            "count" -> handleCountLoop(node, context)
            "while" -> handleWhileLoop(node, context)
            else -> node.next
        }
        context.log("Loop mode=$mode → next=$nextId")
        return NodeResult.success(nextId)
    }

    private fun handleCountLoop(node: WorkflowNode, context: WorkflowContext): String? {
        val params = node.params ?: return node.next
        val times = (params["times"] as? Number)?.toInt() ?: 1
        val counterVar = params["counter_var"]?.toString()?.trim() ?: "loop_index"

        val currentCount = (context.getVariable(counterVar) as? Number)?.toInt() ?: 0

        return if (currentCount < times) {
            context.putVariable(counterVar, currentCount + 1)
            params["body_node"]?.toString()
        } else {
            context.putVariable(counterVar, 0)
            node.next
        }
    }

    private fun handleWhileLoop(node: WorkflowNode, context: WorkflowContext): String? {
        val params = node.params ?: return node.next
        val conditionExpr = params["condition_expr"]?.toString()?.trim().orEmpty()
        val maxIterations = (params["max_iterations"] as? Number)?.toInt() ?: 100

        val iterVar = "_while_iter_${node.id?.trim().orEmpty()}"
        val iterCount = (context.getVariable(iterVar) as? Number)?.toInt() ?: 0

        if (iterCount >= maxIterations) {
            context.putVariable(iterVar, 0)
            return node.next
        }

        val conditionTrue = exprEngine.evaluate(conditionExpr, context).booleanResult
        return if (conditionTrue) {
            context.putVariable(iterVar, iterCount + 1)
            params["body_node"]?.toString()
        } else {
            context.putVariable(iterVar, 0)
            node.next
        }
    }
}
