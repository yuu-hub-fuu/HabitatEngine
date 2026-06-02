package com.ailun.habitat.expression

/**
 * All operators supported by the unified ExpressionEngine.
 * Multi-char operators (>=, <=, !=) are checked BEFORE single-char ones to avoid partial matches.
 */
enum class ExprOperator(
    val symbol: String,
    val category: ExprOperatorCategory,
    val precedence: Int  // higher = evaluated first (for compound expressions)
) {
    // Logical (lowest precedence — evaluated last in compound expressions)
    OR("||", ExprOperatorCategory.LOGICAL, 1),
    AND("&&", ExprOperatorCategory.LOGICAL, 2),
    NOT("!", ExprOperatorCategory.LOGICAL, 3),

    // Comparison
    EQ("==", ExprOperatorCategory.COMPARISON, 4),
    NEQ("!=", ExprOperatorCategory.COMPARISON, 4),

    // Numeric (higher precedence than comparison so e.g. "x > 5" parses as comparison of x to 5,
    // but we treat numeric comparisons as same-level as string comparisons — they are leaf comparisons)
    GT(">", ExprOperatorCategory.NUMERIC, 4),
    LT("<", ExprOperatorCategory.NUMERIC, 4),
    GTE(">=", ExprOperatorCategory.NUMERIC, 4),
    LTE("<=", ExprOperatorCategory.NUMERIC, 4),

    // String
    CONTAINS("contains", ExprOperatorCategory.STRING, 4),
    STARTS_WITH("startswith", ExprOperatorCategory.STRING, 4),
    ENDS_WITH("endswith", ExprOperatorCategory.STRING, 4),

    // Regex
    MATCHES("matches", ExprOperatorCategory.REGEX, 4),

    // Collection
    IN("in", ExprOperatorCategory.COLLECTION, 4),

    // Null checks
    IS_NULL("is_null", ExprOperatorCategory.NULL, 4),
    NOT_NULL("not_null", ExprOperatorCategory.NULL, 4),
    ;

    companion object {
        /**
         * Find the operator in an expression string, checking multi-char operators first.
         * Returns the operator and the START index of the OPERATOR itself (not surrounding spaces),
         * or null if no operator matches.
         */
        fun findIn(expression: String): Pair<ExprOperator, Int>? {
            val sorted = values().sortedByDescending { it.symbol.length }
            for (op in sorted) {
                if (op.category == ExprOperatorCategory.LOGICAL) continue

                // Look for " op " (with spaces) first — safest match
                val spacedIdx = expression.indexOf(" ${op.symbol} ")
                if (spacedIdx > 0) {
                    // Return operator position (+1 to skip the leading space)
                    return Pair(op, spacedIdx + 1)
                }

                // Also support no-space format like "var>=value" or "x>5"
                var idx = expression.indexOf(op.symbol)
                while (idx > 0) {
                    // For multi-char: straightforward — skip past the operator
                    if (op.symbol.length >= 2) {
                        if (idx + op.symbol.length < expression.length) {
                            return Pair(op, idx)
                        }
                    } else {
                        // Single-char: must not be part of >=, <=, !=, ||, &&, =>
                        val nextChar = expression.getOrNull(idx + 1) ?: ' '
                        if (nextChar !in "=>") {
                            return Pair(op, idx)
                        }
                    }
                    idx = expression.indexOf(op.symbol, idx + 1)
                }
            }
            return null
        }

        /** Operators sorted so multi-char are checked before single-char. */
        val multiCharFirst: List<ExprOperator> = values().sortedByDescending { it.symbol.length }
    }
}

enum class ExprOperatorCategory {
    COMPARISON,   // ==, !=
    NUMERIC,      // >, <, >=, <=
    STRING,       // contains, startswith, endswith
    REGEX,        // matches
    COLLECTION,   // in
    NULL,         // is_null, not_null
    LOGICAL,      // &&, ||, !
}
