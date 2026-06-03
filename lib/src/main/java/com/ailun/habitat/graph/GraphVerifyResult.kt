package com.ailun.habitat.graph

/**
 * Complete result of static graph verification.
 */
data class GraphVerifyResult(
    val isValid: Boolean,
    val errors: List<VerifyError> = emptyList(),
    val warnings: List<VerifyWarning> = emptyList(),
    val unreachableNodes: Set<String> = emptySet(),
    val danglingEdges: List<DanglingEdge> = emptyList(),
    val deadLoops: List<DeadLoop> = emptyList(),
    val undefinedVariables: Map<String, List<String>> = emptyMap(),
    val nullVariableRisks: Map<String, List<String>> = emptyMap(),
    val missingSuccessCriteria: Boolean = false,
    val highRiskNodesWithoutGuards: List<String> = emptyList(),
    val branchCoverageReport: BranchCoverageReport? = null,
) {
    /**
     * Human-readable summary suitable for display in the debugger/editor.
     */
    val summary: String
        get() = buildString {
            appendLine("Graph verification: ${if (isValid) "PASSED" else "FAILED"}")
            if (errors.isNotEmpty()) {
                appendLine("  Errors (${errors.size}):")
                errors.forEach { appendLine("    - ${it.message}") }
            }
            if (warnings.isNotEmpty()) {
                appendLine("  Warnings (${warnings.size}):")
                warnings.forEach { appendLine("    - ${it.message}") }
            }
            if (unreachableNodes.isNotEmpty()) {
                appendLine("  Unreachable nodes: ${unreachableNodes.joinToString()}")
            }
            if (highRiskNodesWithoutGuards.isNotEmpty()) {
                appendLine("  High-risk nodes without guards: ${highRiskNodesWithoutGuards.joinToString()}")
            }
            if (missingSuccessCriteria) appendLine("  Missing success criteria")
        }

    companion object {
        fun passed() = GraphVerifyResult(isValid = true)
    }
}

// ──────── Errors (must-fix) ────────

sealed class VerifyError(val message: String) {
    data class MissingNode(val nodeId: String, val referencedBy: String) :
        VerifyError("Node '$nodeId' referenced by '$referencedBy' does not exist")

    data class MissingHandler(val nodeId: String, val type: String) :
        VerifyError("Node '$nodeId' has type '$type' — no handler registered")

    data class UndefinedVariable(val nodeId: String, val varName: String, val usedIn: String) :
        VerifyError("Node '$nodeId' references variable '$varName' in '$usedIn', but it may not be set")

    data class CircularDependency(val cycle: List<String>) :
        VerifyError("Circular dependency detected: ${cycle.joinToString(" → ")}")

    data class InvalidExpression(val nodeId: String, val expr: String, val reason: String) :
        VerifyError("Node '$nodeId' has invalid expression '$expr': $reason")

    data class DanglingBranch(val nodeId: String, val branchName: String, val targetId: String) :
        VerifyError("Node '$nodeId' branch '$branchName' points to non-existent node '$targetId'")

    data class MissingStartNode(val startNodeId: String) :
        VerifyError("Start node '$startNodeId' not found in nodes map")

    data class EmptyNodes :
        VerifyError("Graph has no nodes")
}

// ──────── Warnings (should-fix) ────────

sealed class VerifyWarning(val message: String) {
    data class UnusedNode(val nodeId: String) :
        VerifyWarning("Node '$nodeId' is not reachable from the start node")

    data class NullVariableRisk(val nodeId: String, val varName: String, val context: String) :
        VerifyWarning("Node '$nodeId': variable '$varName' may be null in '$context'")

    data class HighRiskWithoutGuard(val nodeId: String) :
        VerifyWarning("Node '$nodeId' is high-risk but has no guard condition")

    data class CircularRisk(val nodes: List<String>) :
        VerifyWarning("Cycle detected through nodes ${nodes.take(3).joinToString(" → ")}; has a break condition but may still loop indefinitely")

    data class MissingSuccessCriteria :
        VerifyWarning("Workflow has no success criteria defined")

    data class UncoveredBranch(val nodeId: String, val branchName: String) :
        VerifyWarning("Node '$nodeId' branch '$branchName' has no path to exit")
}

// ──────── Structural issues ────────

data class DanglingEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val edgeType: String, // "next", "branches.true", "branches.false", etc.
)

data class DeadLoop(
    val entryNodeId: String,
    val loopNodes: List<String>,
    val hasBreakCondition: Boolean,
)

data class BranchCoverageReport(
    val totalBranches: Int,
    val coveredBranches: Int,
    val uncoveredBranches: List<UncoveredBranch>,
) {
    data class UncoveredBranch(
        val nodeId: String,
        val branchName: String,
        val reason: String,
    )
}
