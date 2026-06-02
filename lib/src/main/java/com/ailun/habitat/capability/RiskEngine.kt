package com.ailun.habitat.capability

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode

/**
 * Assesses the risk of individual nodes and entire workflow graphs.
 *
 * Used by:
 * - GraphVerifier: to flag high-risk nodes without guards
 * - ConfirmationManager: to decide which nodes need user approval
 * - PlanIRCompiler: to auto-insert ACTION_CONFIRM before dangerous steps
 * - Agent Debugger: to display risk badges on nodes
 */
class RiskEngine {

    /**
     * Assess a single node's risk profile.
     */
    fun assessNode(node: WorkflowNode): NodeRiskAssessment {
        val type = node.type ?: ""
        val capabilities = CapabilityMapping.capabilitiesForNodeType(type)
        val riskLevel = CapabilityMapping.riskLevelForNodeType(type)

        val riskReasons = mutableListOf<String>()
        if (riskLevel.score >= RiskLevel.CONFIRMATION_THRESHOLD_SCORE) {
            riskReasons.add("Node type '$type' is inherently ${riskLevel.label} risk")
        }

        // Additional analysis based on params
        val params = node.params ?: emptyMap()

        // File operations: check if delete action
        if (type == "ACTION_FILE_OPERATION") {
            val action = params["action"]?.toString()?.lowercase()
            if (action == "delete") {
                riskReasons.add("File deletion operation detected")
            }
            val path = params["path"]?.toString()
            if (path != null && (path.contains("..") || path.startsWith("/system"))) {
                riskReasons.add("Potentially dangerous file path: $path")
            }
        }

        // Shell: check if command contains dangerous patterns
        if (type == "ACTION_SHELL") {
            val command = params["command"]?.toString() ?: ""
            val dangerousPatterns = listOf(
                "rm " to "File deletion via shell",
                "rm -rf" to "Recursive force deletion",
                "reboot" to "Device reboot",
                "pm uninstall" to "App uninstallation",
                "settings put secure" to "System settings modification",
            )
            for ((pattern, reason) in dangerousPatterns) {
                if (command.contains(pattern, ignoreCase = true)) {
                    riskReasons.add(reason)
                }
            }
            val mode = params["mode"]?.toString()?.lowercase()
            if (mode == "root") {
                riskReasons.add("Root shell execution requested")
            }
        }

        // HTTP: check if POST with variable exfiltration
        if (type == "ACTION_HTTP_REQUEST") {
            val method = params["method"]?.toString()?.uppercase() ?: "GET"
            if (method in listOf("POST", "PUT", "PATCH")) {
                val body = params["body"]?.toString() ?: ""
                if (body.contains("\${")) {
                    riskReasons.add("HTTP $method sends interpolated variable data")
                }
            }
        }

        // Input text: shell mode
        if (type == "ACTION_INPUT_TEXT") {
            val mode = params["mode"]?.toString()?.lowercase()
            if (mode == "shell") {
                riskReasons.add("Shell-based text input — potential injection surface")
            }
        }

        return NodeRiskAssessment(
            nodeId = node.id ?: "",
            nodeType = type,
            capabilities = capabilities,
            riskLevel = riskLevel,
            riskReasons = riskReasons,
            requiresConfirmation = riskLevel.score >= RiskLevel.CONFIRMATION_THRESHOLD_SCORE || riskReasons.isNotEmpty(),
        )
    }

    /**
     * Assess the overall risk of an entire workflow graph.
     */
    fun assessGraph(graph: WorkflowGraph): GraphRiskAssessment {
        val nodes = graph.nodes ?: emptyMap()
        val nodeAssessments = nodes.mapValues { (_, node) -> assessNode(node) }

        val overallRisk = nodeAssessments.values
            .map { it.riskLevel }
            .fold(RiskLevel.LOW) { acc, risk -> RiskLevel.max(acc, risk) }

        val maxCapabilityRisk = nodeAssessments.values
            .flatMap { it.capabilities }
            .maxByOrNull { it.defaultRisk.score }
            ?.defaultRisk ?: RiskLevel.LOW

        return GraphRiskAssessment(
            overallRisk = overallRisk,
            nodeAssessments = nodeAssessments,
            maxCapabilityRisk = maxCapabilityRisk,
        )
    }

    /**
     * Returns true if this node needs user confirmation before execution.
     */
    fun requiresConfirmation(node: WorkflowNode): Boolean {
        return assessNode(node).requiresConfirmation
    }

    /**
     * Returns all (nodeId, riskLevel) pairs for dangerous nodes in a graph.
     */
    fun dangerousActionsIn(graph: WorkflowGraph): List<Pair<String, RiskLevel>> {
        val nodes = graph.nodes ?: return emptyList()
        return nodes.values
            .filter { assessNode(it).requiresConfirmation }
            .map { Pair(it.id ?: "unknown", assessNode(it).riskLevel) }
    }
}

/**
 * Risk assessment for a single node.
 */
data class NodeRiskAssessment(
    val nodeId: String,
    val nodeType: String,
    val capabilities: Set<Capability>,
    val riskLevel: RiskLevel,
    val riskReasons: List<String>,
    val requiresConfirmation: Boolean,
)

/**
 * Aggregated risk assessment for a workflow graph.
 */
data class GraphRiskAssessment(
    val overallRisk: RiskLevel,
    val nodeAssessments: Map<String, NodeRiskAssessment>,
    val maxCapabilityRisk: RiskLevel,
)
