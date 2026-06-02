package com.ailun.habitat.execution

import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.capability.RiskLevel

/**
 * Rich return type for the V2 handler interface.
 *
 * Compared to the V1 interface (which returns String?), NodeResult provides:
 * - Explicit success/failure signaling
 * - Error messages for logging and recovery
 * - Rollback and compensation for transactional error handling
 * - Variable change tracking for trajectory recording
 * - Risk event reporting
 */
data class NodeResult(
    val success: Boolean,
    val nextNodeId: String?,
    val error: String? = null,
    val variableChanges: Map<String, DiffEntry> = emptyMap(),
    val riskEvents: List<RiskEvent> = emptyList(),
    val rollbackNodeId: String? = null,
    val compensateAction: CompensateAction? = null,
) {
    companion object {
        fun next(nodeId: String?): NodeResult = NodeResult(true, nodeId)
        fun stop(): NodeResult = NodeResult(true, null)
        fun error(msg: String, rollback: String? = null): NodeResult =
            NodeResult(false, null, error = msg, rollbackNodeId = rollback)
        fun fromBranch(node: WorkflowNode, key: String): NodeResult =
            next(node.branches?.get(key))
    }
}

data class DiffEntry(val before: Any?, val after: Any?)

data class RiskEvent(
    val type: String,
    val severity: RiskLevel,
    val description: String,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class CompensateAction(
    val handlerType: String,
    val params: Map<String, Any>,
)
