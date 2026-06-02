package com.ailun.habitat.expression

/**
 * Result of an expression evaluation.
 *
 * The [explanation] field is designed to be LLM-readable for debugging:
 *   "left(sum=17) > right(threshold=15) → true"
 *   "left(status) == right('ready') → false (got 'pending')"
 */
data class ExpressionResult(
    /** Whether the evaluation succeeded (no parse errors, no type mismatches). */
    val success: Boolean,

    /** The boolean outcome — true if the expression's condition holds. */
    val booleanResult: Boolean = false,

    /** The typed left-hand operand value (after variable resolution). */
    val leftValue: Any? = null,

    /** The typed right-hand operand value (after variable resolution), null for unary ops. */
    val rightValue: Any? = null,

    /** The operator that was evaluated, null for bare-variable expressions. */
    val operator: ExprOperator? = null,

    /** The raw expression string as provided. */
    val expression: String,

    /**
     * Human-readable explanation of the evaluation.
     * Example: "left(click_success=true) == right(true) → true"
     * When the expression is a bare variable: "variable(is_daytime) → true"
     */
    val explanation: String
) {
    companion object {
        fun success(
            booleanResult: Boolean,
            leftValue: Any?,
            rightValue: Any?,
            operator: ExprOperator?,
            expression: String,
            explanation: String,
        ) = ExpressionResult(
            success = true,
            booleanResult = booleanResult,
            leftValue = leftValue,
            rightValue = rightValue,
            operator = operator,
            expression = expression,
            explanation = explanation,
        )

        fun failure(expression: String, reason: String) = ExpressionResult(
            success = false,
            booleanResult = false,
            expression = expression,
            explanation = "FAILED: $reason",
        )

        fun bareVariable(name: String, value: Any?): ExpressionResult {
            val bool = when (value) {
                is Boolean -> value
                is String -> value.equals("true", ignoreCase = true)
                is Number -> value.toDouble() != 0.0
                else -> false
            }
            return success(
                booleanResult = bool,
                leftValue = value,
                rightValue = null,
                operator = null,
                expression = name,
                explanation = "variable($name=$value) → $bool",
            )
        }

        fun builtinResult(name: String, value: Boolean): ExpressionResult = success(
            booleanResult = value,
            leftValue = value,
            rightValue = null,
            operator = null,
            expression = name,
            explanation = "builtin($name) → $value",
        )
    }
}
