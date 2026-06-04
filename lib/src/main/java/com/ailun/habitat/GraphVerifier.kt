package com.ailun.habitat

/**
 * Lightweight issue type used by [DryRunEngine] and [GraphVerificationResult].
 * The comprehensive graph verifier lives in [com.ailun.habitat.graph.GraphVerifier].
 */
data class GraphIssue(
    val level: Level,
    val nodeId: String?,
    val message: String
) {
    enum class Level { ERROR, WARNING }
}

data class GraphVerificationResult(
    val issues: List<GraphIssue>
) {
    val hasError: Boolean get() = issues.any { it.level == GraphIssue.Level.ERROR }
    fun throwIfInvalid() {
        if (hasError) {
            throw IllegalArgumentException(
                issues.filter { it.level == GraphIssue.Level.ERROR }
                    .joinToString("\n") { "[${it.nodeId ?: "graph"}] ${it.message}" }
            )
        }
    }
}
