package com.ailun.habitat

data class ExpressionResult(
    val value: Boolean,
    val explanation: String
)

object ExpressionEngine {
    private val operators = listOf(">=", "<=", "!=", "==", "contains", "startswith", "endswith", ">", "<")

    fun eval(expr: String, context: WorkflowContext): ExpressionResult {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return ExpressionResult(false, "空表达式")

        val boolVar = context.getVariable(trimmed)
        if (boolVar is Boolean) {
            return ExpressionResult(boolVar, "布尔变量 $trimmed = $boolVar")
        }

        for (op in operators) {
            val parts = splitByOperator(trimmed, op) ?: continue
            val leftRaw = parts.first
            val rightRaw = parts.second

            val leftValue = resolve(leftRaw, context)
            val rightValue = resolve(rightRaw, context)

            val result = when (op) {
                "==" -> leftValue.toString() == rightValue.toString()
                "!=" -> leftValue.toString() != rightValue.toString()
                ">" -> compareNumber(leftValue, rightValue) { a, b -> a > b }
                "<" -> compareNumber(leftValue, rightValue) { a, b -> a < b }
                ">=" -> compareNumber(leftValue, rightValue) { a, b -> a >= b }
                "<=" -> compareNumber(leftValue, rightValue) { a, b -> a <= b }
                "contains" -> leftValue.toString().contains(rightValue.toString(), ignoreCase = true)
                "startswith" -> leftValue.toString().startsWith(rightValue.toString(), ignoreCase = true)
                "endswith" -> leftValue.toString().endsWith(rightValue.toString(), ignoreCase = true)
                else -> false
            }
            return ExpressionResult(result, "$leftRaw=$leftValue $op $rightRaw=$rightValue → $result")
        }

        return ExpressionResult(false, "无法识别表达式: $expr")
    }

    private fun splitByOperator(expr: String, op: String): Pair<String, String>? {
        val token = " $op "
        val idx = expr.indexOf(token)
        if (idx > 0) return expr.substring(0, idx).trim() to expr.substring(idx + token.length).trim()

        val idxNoSpace = expr.indexOf(op)
        if (idxNoSpace > 0 && op in listOf("==", "!=", ">=", "<=", ">", "<")) {
            return expr.substring(0, idxNoSpace).trim() to expr.substring(idxNoSpace + op.length).trim()
        }
        return null
    }

    private fun resolve(raw: String, context: WorkflowContext): Any? {
        val trimmed = raw.trim().removeSurrounding("\"").removeSurrounding("'")
        return context.getVariable(trimmed) ?: trimmed
    }

    private fun compareNumber(left: Any?, right: Any?, op: (Double, Double) -> Boolean): Boolean {
        val a = left?.toString()?.toDoubleOrNull() ?: return false
        val b = right?.toString()?.toDoubleOrNull() ?: return false
        return op(a, b)
    }
}
