package com.ailun.habitat

data class NodeResult(
    val success: Boolean,
    val nextNodeId: String?,
    val errorMessage: String? = null,
    val outputVariables: Map<String, Any?> = emptyMap(),
    val riskEvent: RiskEvent? = null
) {
    companion object {
        fun success(next: String?, vars: Map<String, Any?> = emptyMap()): NodeResult =
            NodeResult(success = true, nextNodeId = next, outputVariables = vars)

        fun failure(next: String?, error: String): NodeResult =
            NodeResult(success = false, nextNodeId = next, errorMessage = error)
    }
}

data class RiskEvent(
    val level: RiskLevel,
    val capability: String,
    val reason: String,
    val requiresConfirmation: Boolean = false
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

/** Extension helper for quickly converting from V1-style String? return. */
fun WorkflowNode.nextResult(): NodeResult = NodeResult.success(this.next)
