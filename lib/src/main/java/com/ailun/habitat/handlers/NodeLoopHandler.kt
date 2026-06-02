package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.expression.ExpressionEngine
import com.ailun.habitat.expression.IVariableProvider

/**
 * [ACTION_LOOP]：简单循环控制（计数或条件）。
 *
 * params：
 * - `mode`: `count`（计数循环）| `while`（条件循环）
 * - 若 mode=count：`times`（循环次数），`counter_var`（计数变量名，默认"loop_index"）
 * - 若 mode=while：`condition_expr`（条件表达式），`max_iterations`（最多迭代次数，默认100）
 * - `body_node`：循环体起始节点 ID
 * - `next`：循环结束后的下一节点 ID
 *
 * When [engine] is non-null, while-loop conditions are evaluated through
 * the unified ExpressionEngine, supporting full operator set (==, !=, >, <, etc.).
 * When null, falls back to legacy ==/!= only (backward compat).
 */
class NodeLoopHandler(
    private val engine: ExpressionEngine? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val params = node.params ?: return node.next
        val mode = params["mode"]?.toString()?.trim()?.lowercase() ?: "count"

        val result = when (mode) {
            "count" -> handleCountLoop(node, context)
            "while" -> handleWhileLoop(node, context)
            else -> node.next
        }
        context.log("Loop mode=$mode → next=$result")
        return result
    }

    private fun handleCountLoop(node: WorkflowNode, context: WorkflowContext): String? {
        val params = node.params ?: return node.next
        val times = (params["times"] as? Number)?.toInt() ?: 1
        val counterVar = params["counter_var"]?.toString()?.trim() ?: "loop_index"

        val currentCount = (context.getVariable(counterVar) as? Number)?.toInt() ?: 0

        return if (currentCount < times) {
            context.putVariable(counterVar, currentCount + 1)
            params["body_node"]?.toString() // 进入循环体
        } else {
            context.putVariable(counterVar, 0) // 重置
            node.next // 跳出循环
        }
    }

    private fun handleWhileLoop(node: WorkflowNode, context: WorkflowContext): String? {
        val params = node.params ?: return node.next
        val conditionExpr = params["condition_expr"]?.toString()?.trim().orEmpty()
        val maxIterations = (params["max_iterations"] as? Number)?.toInt() ?: 100

        val iterVar = "_while_iter_count_"
        val iterCount = (context.getVariable(iterVar) as? Number)?.toInt() ?: 0

        if (iterCount >= maxIterations) {
            context.putVariable(iterVar, 0)
            return node.next // 达到迭代上限，跳出
        }

        val conditionTrue = evaluateCondition(conditionExpr, context)
        return if (conditionTrue) {
            context.putVariable(iterVar, iterCount + 1)
            params["body_node"]?.toString() // 继续循环体
        } else {
            context.putVariable(iterVar, 0)
            node.next // 条件为假，跳出
        }
    }

    private fun evaluateCondition(expr: String, context: WorkflowContext): Boolean {
        if (expr.isEmpty()) return false

        // Use unified engine when available
        if (engine != null) {
            val provider = object : IVariableProvider {
                override fun getVariable(key: String): Any? = context.getVariable(key)
            }
            val result = engine.evaluate(expr, provider)
            context.log("  ExpressionEngine: ${result.explanation}")
            return result.booleanResult
        }

        // ── Legacy fallback: only == and != ──
        return when {
            expr.contains("==") -> {
                val parts = expr.split("==")
                if (parts.size == 2) {
                    val varName = parts[0].trim()
                    val expectedValue = parts[1].trim()
                    val actualValue = context.getVariable(varName)?.toString() ?: ""
                    actualValue == expectedValue
                } else false
            }
            expr.contains("!=") -> {
                val parts = expr.split("!=")
                if (parts.size == 2) {
                    val varName = parts[0].trim()
                    val unexpectedValue = parts[1].trim()
                    val actualValue = context.getVariable(varName)?.toString() ?: ""
                    actualValue != unexpectedValue
                } else false
            }
            else -> false
        }
    }
}

