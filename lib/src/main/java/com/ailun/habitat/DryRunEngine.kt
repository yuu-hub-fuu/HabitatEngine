package com.ailun.habitat

import com.ailun.habitat.capability.RiskEngine
import com.ailun.habitat.expression.ExpressionEngine
import com.ailun.habitat.graph.GraphVerifier

/**
 * Pre-execution dry-run inspector.
 *
 * Runs GraphVerifier (structural + risk + expression + handler checks) and adds
 * node-level static risk analysis. Supports two policy modes.
 */
object DryRunEngine {

    enum class Policy {
        /** Warnings are logged but do not block execution. */
        NORMAL,
        /** High-risk warnings (risk without guard/confirm) block execution. */
        STRICT,
    }

    /**
     * Inspect [graph] for issues using the given [factory] for handler registration checks.
     *
     * @param factory Used to verify every node type has a registered handler. Pass null to skip.
     * @param policy Controls whether high-risk warnings become errors.
     */
    fun inspect(
        graph: WorkflowGraph,
        factory: NodeHandlerFactory? = null,
        policy: Policy = Policy.NORMAL,
    ): GraphVerificationResult {
        val exprEngine = ExpressionEngine()
        val riskEngine = RiskEngine()
        val verifier = GraphVerifier(exprEngine, riskEngine, factory)
        val verifyResult = verifier.verify(graph)

        val baseIssues = mutableListOf<GraphIssue>()

        // ── Convert GraphVerifier errors ──
        verifyResult.errors.forEach { err ->
            baseIssues += GraphIssue(GraphIssue.Level.ERROR, err.message, "")
        }

        // ── Convert GraphVerifier warnings ──
        verifyResult.warnings.forEach { warn ->
            val level = if (policy == Policy.STRICT && warn is com.ailun.habitat.graph.VerifyWarning.HighRiskWithoutGuard) {
                GraphIssue.Level.ERROR  // Strict mode: unguarded high-risk blocks execution
            } else {
                GraphIssue.Level.WARNING
            }
            baseIssues += GraphIssue(level, warn.message, "")
        }

        // ── Node-level static risk (shell, file delete, HTTP) ──
        val nodes = graph.nodes.orEmpty()
        for ((id, node) in nodes) {
            val staticRiskMsg = staticRisk(node)
            if (staticRiskMsg != null) {
                val level = if (policy == Policy.STRICT) GraphIssue.Level.ERROR else GraphIssue.Level.WARNING
                baseIssues += GraphIssue(level, id, staticRiskMsg)
            }

            node.requiredCapabilities?.forEach { cap ->
                if (cap.isBlank()) {
                    baseIssues += GraphIssue(GraphIssue.Level.ERROR, id, "required_capabilities 包含空值")
                }
            }

            // Check empty capabilities set (no capabilities declared)
            if (node.requiredCapabilities != null && node.requiredCapabilities!!.isEmpty()) {
                baseIssues += GraphIssue(GraphIssue.Level.WARNING, id, "required_capabilities is empty — no authorization gate")
            }
        }

        return GraphVerificationResult(baseIssues)
    }

    private fun staticRisk(node: WorkflowNode): String? {
        return when (node.type) {
            NodeHandlerFactory.ACTION_SHELL -> "包含 Shell 执行节点，必须确认 capability"
            NodeHandlerFactory.ACTION_FILE_OPERATION -> {
                val action = node.params?.get("action")?.toString()?.lowercase()
                if (action == "delete") "包含文件删除节点，必须确认 capability" else null
            }
            NodeHandlerFactory.ACTION_HTTP_REQUEST -> "包含 HTTP 请求节点，注意外传变量"
            else -> null
        }
    }
}
