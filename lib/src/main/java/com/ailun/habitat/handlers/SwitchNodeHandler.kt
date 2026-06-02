package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.expression.ExpressionEngine
import com.ailun.habitat.expression.IVariableProvider

/**
 * [CONDITION_SWITCH]：根据 `expression` 选择 [WorkflowNode.branches]。
 * 支持内置表达式及通用的 `var_name == value` 格式。
 *
 * When [engine] is non-null, delegates to the unified ExpressionEngine for
 * full operator support and detailed explanation output.
 * When null, falls back to legacy string-splitting evaluation (backward compat).
 */
class SwitchNodeHandler(
    private val engine: ExpressionEngine? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val expr = node.params?.get("expression")?.toString()?.trim().orEmpty()
        val result = evaluate(expr, context)
        context.log("Switch '$expr' → $result")
        return node.branches?.get(if (result) "true" else "false")
    }

    private fun evaluate(expr: String, ctx: WorkflowContext): Boolean {
        if (expr.isEmpty()) return false

        // Use unified engine when available — produces detailed explanation log
        if (engine != null) {
            val provider = object : IVariableProvider {
                override fun getVariable(key: String): Any? = ctx.getVariable(key)
            }
            val result = engine.evaluate(expr, provider)
            // Log the engine's explanation for debugging
            ctx.log("  ExpressionEngine: ${result.explanation}")
            return result.booleanResult
        }

        // ── Legacy fallback (preserved for backward compatibility) ──

        // 内置表达式
        if (expr == "is_daytime == true" || expr == "is_daytime==true") {
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return h in 8..17
        }

        // 通用 var operator value 格式
        val parts = tryParseComparison(expr)
        if (parts != null) {
            val (varName, op, expected) = parts
            val actual = ctx.getVariable(varName)?.toString() ?: "null"
            return when (op) {
                "==" -> actual == expected
                "!=" -> actual != expected
                ">" -> (actual.toDoubleOrNull() ?: 0.0) > (expected.toDoubleOrNull() ?: 0.0)
                "<" -> (actual.toDoubleOrNull() ?: 0.0) < (expected.toDoubleOrNull() ?: 0.0)
                ">=" -> (actual.toDoubleOrNull() ?: 0.0) >= (expected.toDoubleOrNull() ?: 0.0)
                "<=" -> (actual.toDoubleOrNull() ?: 0.0) <= (expected.toDoubleOrNull() ?: 0.0)
                "contains" -> actual.contains(expected, ignoreCase = true)
                "startswith" -> actual.startsWith(expected, ignoreCase = true)
                "endswith" -> actual.endsWith(expected, ignoreCase = true)
                else -> actual == expected
            }
        }

        // 直接取布尔变量
        val boolVal = ctx.getVariable(expr)
        if (boolVal is Boolean) return boolVal

        return false
    }

    /** 解析 "var == value" 或 "var != value" 等通用比较格式 */
    private fun tryParseComparison(expr: String): Triple<String, String, String>? {
        val ops = listOf("==", "!=", ">=", "<=", ">", "<", "contains", "startswith", "endswith")
        for (op in ops) {
            val idx = expr.indexOf(" $op ")
            val idxNoSpace = expr.indexOf(op)
            if (idx > 0) {
                val varName = expr.substring(0, idx).trim()
                val value = expr.substring(idx + op.length + 2).trim()
                return Triple(varName, op, value)
            } else if (idxNoSpace > 0 && op.length >= 2) {
                // 无空格如 "var==value"
                val varName = expr.substring(0, idxNoSpace).trim()
                val value = expr.substring(idxNoSpace + op.length).trim()
                return Triple(varName, op, value)
            }
        }
        return null
    }
}
