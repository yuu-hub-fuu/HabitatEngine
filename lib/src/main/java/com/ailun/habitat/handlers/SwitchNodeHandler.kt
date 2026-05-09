package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import java.util.Calendar

/**
 * [CONDITION_SWITCH]：根据 `expression` 选择 [WorkflowNode.branches]。
 * 支持内置表达式及通用的 `var_name == value` 格式。
 */
class SwitchNodeHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val expr = node.params?.get("expression")?.toString()?.trim().orEmpty()
        val result = evaluate(expr, context)
        context.log("Switch '$expr' → $result")
        return node.branches?.get(if (result) "true" else "false")
    }

    @Suppress("IfThenToElvis")
    private fun evaluate(expr: String, ctx: WorkflowContext): Boolean {
        if (expr.isEmpty()) return false

        // 内置表达式
        if (expr == "is_daytime == true" || expr == "is_daytime==true") {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
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
