package com.ailun.habitat.expression

import java.util.Calendar

/**
 * Variable provider interface for the expression engine.
 * Decouples the engine from WorkflowContext so it can be used in static analysis too.
 */
interface IVariableProvider {
    fun getVariable(key: String): Any?
}

/**
 * AST node for parsed expressions — used by static analysis (GraphVerifier).
 */
sealed class ExpressionNode {
    data class Comparison(
        val leftName: String,
        val operator: ExprOperator,
        val rightRaw: String,  // literal string or another variable name
        val rawExpression: String,
    ) : ExpressionNode()

    data class LogicalAnd(val left: ExpressionNode, val right: ExpressionNode) : ExpressionNode()
    data class LogicalOr(val left: ExpressionNode, val right: ExpressionNode) : ExpressionNode()
    data class LogicalNot(val operand: ExpressionNode) : ExpressionNode()
    data class BareVariable(val name: String) : ExpressionNode()
    data class Builtin(val name: String) : ExpressionNode()

    /** Collect all variable names referenced in this AST subtree. */
    fun referencedVariables(): Set<String> = when (this) {
        is Comparison -> setOfNotNull(
            leftName.takeIf { it.isNotEmpty() },
            rightRaw.takeIf { it.isNotEmpty() },
        )
        is LogicalAnd -> left.referencedVariables() + right.referencedVariables()
        is LogicalOr -> left.referencedVariables() + right.referencedVariables()
        is LogicalNot -> operand.referencedVariables()
        is BareVariable -> setOf(name)
        is Builtin -> emptySet()
    }
}

/**
 * Unified Expression Engine.
 *
 * Replaces the fragmented expression evaluation logic previously duplicated across
 * [SwitchNodeHandler], [NodeAdvancedSwitchHandler], and [NodeLoopHandler].
 *
 * Supports:
 * - All comparison operators: ==, !=, >, <, >=, <=
 * - All string operators: contains, startswith, endswith
 * - Regex: matches
 * - Collection: in
 * - Null checks: is_null, not_null
 * - Compound logic: &&, ||, !
 * - Built-in variables: is_daytime, is_nighttime
 * - Null-safe variable resolution with type coercion
 * - Detailed explanation output for LLM debugging
 */
class ExpressionEngine {
    /**
     * Evaluate an expression string against variables from [provider].
     *
     * Expression forms supported:
     * - "var == value" — comparison (both sides resolve variables)
     * - "var operator value" — comparison with any binary operator
     * - "expr && expr" — logical AND
     * - "expr || expr" — logical OR
     * - "!expr" — logical NOT
     * - "var" — bare variable (true if Boolean=true, Number≠0, String="true")
     * - "is_daytime" / "is_nighttime" — built-in
     */
    fun evaluate(expression: String, provider: IVariableProvider): ExpressionResult {
        if (expression.isBlank()) {
            return ExpressionResult.failure(expression, "Expression is blank")
        }

        return try {
            evaluateCompound(expression.trim(), provider)
        } catch (e: Exception) {
            ExpressionResult.failure(expression, "Evaluation error: ${e.message}")
        }
    }

    /**
     * Parse an expression into its AST without evaluating. Used by GraphVerifier
     * to statically check variable references.
     */
    fun parse(expression: String): ExpressionNode {
        if (expression.isBlank()) {
            return ExpressionNode.BareVariable("")
        }
        return parseNode(expression.trim())
    }

    // ─────────────────────────────────────────
    // Compound expression evaluation
    // ─────────────────────────────────────────

    private fun evaluateCompound(expr: String, provider: IVariableProvider): ExpressionResult {
        // Check for NOT prefix (unary)
        if (expr.startsWith("!")) {
            val inner = expr.substring(1).trim()
            val innerResult = evaluateCompound(inner, provider)
            return ExpressionResult.success(
                booleanResult = !innerResult.booleanResult,
                leftValue = innerResult.booleanResult,
                rightValue = null,
                operator = ExprOperator.NOT,
                expression = expr,
                explanation = "!(${innerResult.explanation}) → ${!innerResult.booleanResult}",
            )
        }

        // Check for || (lowest precedence)
        val orIdx = findLogicalOp(expr, "||")
        if (orIdx != null) {
            val left = expr.substring(0, orIdx).trim()
            val right = expr.substring(orIdx + 2).trim()
            val leftResult = evaluateCompound(left, provider)
            if (leftResult.booleanResult) {
                // Short-circuit: true || anything = true
                return ExpressionResult.success(
                    booleanResult = true,
                    leftValue = leftResult.booleanResult,
                    rightValue = null,
                    operator = ExprOperator.OR,
                    expression = expr,
                    explanation = "(${leftResult.explanation}) || (not evaluated — short-circuit) → true",
                )
            }
            val rightResult = evaluateCompound(right, provider)
            val bool = leftResult.booleanResult || rightResult.booleanResult
            return ExpressionResult.success(
                booleanResult = bool,
                leftValue = leftResult.booleanResult,
                rightValue = rightResult.booleanResult,
                operator = ExprOperator.OR,
                expression = expr,
                explanation = "(${leftResult.explanation}) || (${rightResult.explanation}) → $bool",
            )
        }

        // Check for &&
        val andIdx = findLogicalOp(expr, "&&")
        if (andIdx != null) {
            val left = expr.substring(0, andIdx).trim()
            val right = expr.substring(andIdx + 2).trim()
            val leftResult = evaluateCompound(left, provider)
            if (!leftResult.booleanResult) {
                // Short-circuit: false && anything = false
                return ExpressionResult.success(
                    booleanResult = false,
                    leftValue = leftResult.booleanResult,
                    rightValue = null,
                    operator = ExprOperator.AND,
                    expression = expr,
                    explanation = "(${leftResult.explanation}) && (not evaluated — short-circuit) → false",
                )
            }
            val rightResult = evaluateCompound(right, provider)
            val bool = leftResult.booleanResult && rightResult.booleanResult
            return ExpressionResult.success(
                booleanResult = bool,
                leftValue = leftResult.booleanResult,
                rightValue = rightResult.booleanResult,
                operator = ExprOperator.AND,
                expression = expr,
                explanation = "(${leftResult.explanation}) && (${rightResult.explanation}) → $bool",
            )
        }

        // Single comparison or bare variable
        return evaluateSimple(expr, provider)
    }

    private fun evaluateSimple(expr: String, provider: IVariableProvider): ExpressionResult {
        // Built-in variables
        if (expr == "is_daytime" || expr == "is_daytime == true" || expr == "is_daytime==true") {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val result = h in 8..17
            return ExpressionResult.builtinResult("is_daytime", result)
        }
        if (expr == "is_nighttime" || expr == "is_nighttime == true" || expr == "is_nighttime==true") {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val result = h !in 8..17
            return ExpressionResult.builtinResult("is_nighttime", result)
        }

        // Try to find a comparison operator
        val opInfo = ExprOperator.findIn(expr)
        if (opInfo != null) {
            val (op, idx) = opInfo
            val leftName = expr.substring(0, idx).trim()
            val rightRaw = expr.substring(idx + op.symbol.length).trim()

            return evaluateComparison(leftName, op, rightRaw, expr, provider)
        }

        // Bare variable or literal
        val varValue = provider.getVariable(expr)
        if (varValue != null) {
            return ExpressionResult.bareVariable(expr, varValue)
        }

        // Literal boolean
        if (expr.equals("true", ignoreCase = true)) {
            return ExpressionResult.success(true, true, null, null, expr, "literal(true) → true")
        }
        if (expr.equals("false", ignoreCase = true)) {
            return ExpressionResult.success(true, false, null, null, expr, "literal(false) → false")
        }

        // Unknown — treat as false
        return ExpressionResult.success(
            booleanResult = false,
            leftValue = expr,
            rightValue = null,
            operator = null,
            expression = expr,
            explanation = "unknown('$expr') → false",
        )
    }

    private fun evaluateComparison(
        leftName: String,
        op: ExprOperator,
        rightRaw: String,
        rawExpr: String,
        provider: IVariableProvider,
    ): ExpressionResult {
        val leftVal: Any? = provider.getVariable(leftName) ?: leftName
        val rightVal: Any? = provider.getVariable(rightRaw) ?: rightRaw

        val leftNum = toNumber(leftVal)
        val rightNum = toNumber(rightVal)

        val (bool, detail) = when (op) {
            ExprOperator.EQ -> {
                val result = leftVal.toString() == rightVal.toString()
                Pair(result, "'${leftVal}' == '${rightVal}' → $result")
            }
            ExprOperator.NEQ -> {
                val result = leftVal.toString() != rightVal.toString()
                Pair(result, "'${leftVal}' != '${rightVal}' → $result")
            }
            ExprOperator.GT -> numericOp(leftNum, rightNum, leftVal, rightVal, ">") { a, b -> a > b }
            ExprOperator.LT -> numericOp(leftNum, rightNum, leftVal, rightVal, "<") { a, b -> a < b }
            ExprOperator.GTE -> numericOp(leftNum, rightNum, leftVal, rightVal, ">=") { a, b -> a >= b }
            ExprOperator.LTE -> numericOp(leftNum, rightNum, leftVal, rightVal, "<=") { a, b -> a <= b }
            ExprOperator.CONTAINS -> {
                val result = leftVal.toString().contains(rightVal.toString(), ignoreCase = true)
                Pair(result, "'${leftVal}'.contains('${rightVal}') → $result")
            }
            ExprOperator.STARTS_WITH -> {
                val result = leftVal.toString().startsWith(rightVal.toString(), ignoreCase = true)
                Pair(result, "'${leftVal}'.startsWith('${rightVal}') → $result")
            }
            ExprOperator.ENDS_WITH -> {
                val result = leftVal.toString().endsWith(rightVal.toString(), ignoreCase = true)
                Pair(result, "'${leftVal}'.endsWith('${rightVal}') → $result")
            }
            ExprOperator.MATCHES -> {
                val result = try {
                    rightVal.toString().toRegex().containsMatchIn(leftVal.toString())
                } catch (_: Exception) {
                    false
                }
                Pair(result, "'${leftVal}'.matches(/${rightVal}/) → $result")
            }
            ExprOperator.IN -> {
                // Right side is a comma/semicolon/pipe-delimited list
                val list = rightVal.toString().split(",", ";", "|").map { it.trim() }
                val result = leftVal.toString() in list
                Pair(result, "'${leftVal}' in [${list.joinToString(", ")}] → $result")
            }
            ExprOperator.IS_NULL -> {
                val result = leftVal == null || leftVal.toString() == "null" || leftVal.toString().isEmpty()
                Pair(result, "'${leftVal}'.is_null → $result")
            }
            ExprOperator.NOT_NULL -> {
                val result = !(leftVal == null || leftVal.toString() == "null" || leftVal.toString().isEmpty())
                Pair(result, "'${leftVal}'.not_null → $result")
            }
            else -> Pair(false, "Unsupported operator: ${op.symbol}")
        }

        val explanation = buildString {
            append("left($leftName=${leftVal ?: "null"}) ${op.symbol} right($rightRaw=${rightVal ?: "null"}) → $bool")
            append(" | detail: $detail")
        }

        return ExpressionResult.success(
            booleanResult = bool,
            leftValue = leftVal,
            rightValue = rightVal,
            operator = op,
            expression = rawExpr,
            explanation = explanation,
        )
    }

    // ─────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────

    /** Find a logical operator not inside quotes or parentheses. */
    private fun findLogicalOp(expr: String, op: String): Int? {
        var depth = 0
        var inQuote = false
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c == '"' || c == '\'' -> inQuote = !inQuote
                c == '(' && !inQuote -> depth++
                c == ')' && !inQuote -> depth--
                depth == 0 && !inQuote && expr.regionMatches(i, op, 0, op.length, ignoreCase = false) -> {
                    // Verify it's not part of a longer symbol
                    val beforeOk = i == 0 || expr[i - 1].isWhitespace()
                    val afterOk = i + op.length >= expr.length || expr[i + op.length].isWhitespace()
                    if (beforeOk && afterOk) return i
                }
            }
            i++
        }
        return null
    }

    private fun toNumber(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun numericOp(
        leftNum: Double?,
        rightNum: Double?,
        leftVal: Any?,
        rightVal: Any?,
        opSymbol: String,
        fn: (Double, Double) -> Boolean,
    ): Pair<Boolean, String> {
        if (leftNum == null || rightNum == null) {
            return Pair(false, "non-numeric: left='${leftVal}', right='${rightVal}'")
        }
        val result = fn(leftNum, rightNum)
        return Pair(result, "$leftNum $opSymbol $rightNum → $result")
    }

    // ─────────────────────────────────────────
    // AST parsing (for static analysis)
    // ─────────────────────────────────────────

    private fun parseNode(expr: String): ExpressionNode {
        if (expr.startsWith("!")) {
            return ExpressionNode.LogicalNot(parseNode(expr.substring(1).trim()))
        }

        val orIdx = findLogicalOp(expr, "||")
        if (orIdx != null) {
            return ExpressionNode.LogicalOr(
                parseNode(expr.substring(0, orIdx).trim()),
                parseNode(expr.substring(orIdx + 2).trim()),
            )
        }

        val andIdx = findLogicalOp(expr, "&&")
        if (andIdx != null) {
            return ExpressionNode.LogicalAnd(
                parseNode(expr.substring(0, andIdx).trim()),
                parseNode(expr.substring(andIdx + 2).trim()),
            )
        }

        // Built-ins
        if (expr == "is_daytime" || expr.startsWith("is_daytime")) {
            return ExpressionNode.Builtin("is_daytime")
        }
        if (expr == "is_nighttime" || expr.startsWith("is_nighttime")) {
            return ExpressionNode.Builtin("is_nighttime")
        }

        // Comparison
        val opInfo = ExprOperator.findIn(expr)
        if (opInfo != null) {
            val (op, idx) = opInfo
            val leftName = expr.substring(0, idx).trim()
            val rightRaw = expr.substring(idx + op.symbol.length).trim()
            return ExpressionNode.Comparison(leftName, op, rightRaw, expr)
        }

        // Bare variable or literal
        return ExpressionNode.BareVariable(expr)
    }
}
