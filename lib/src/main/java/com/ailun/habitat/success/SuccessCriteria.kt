package com.ailun.habitat.success

/**
 * Workflow-level or node-level success criteria.
 * Evaluated after execution to verify the action achieved its intended effect.
 */
data class SuccessCriteria(
    val conditions: List<SuccessCondition>,
    val requireAll: Boolean = true,
)

data class SuccessCondition(
    val type: SuccessConditionType,
    val expression: String,
    val description: String = "",
)

enum class SuccessConditionType {
    /** Current screen contains the specified text. expression = text to find. */
    SCREEN_CONTAINS_TEXT,

    /** A variable satisfies a condition. expression = "var op value". */
    VARIABLE_SATISFIES,

    /** A file exists at the given path with content matching. expression = path. */
    FILE_EXISTS_WITH_CONTENT,

    /** HTTP response status matches. expression = "status == 200". */
    HTTP_STATUS_MATCHES,

    /** A specific UI element is visible. expression = element selector. */
    ELEMENT_VISIBLE,
}

data class SuccessEvaluation(
    val passed: Boolean,
    val failedConditions: List<FailedCondition> = emptyList(),
    val evaluationLog: String = "",
    val evaluatedAt: Long = System.currentTimeMillis(),
)

data class FailedCondition(
    val condition: SuccessCondition,
    val reason: String,
)
