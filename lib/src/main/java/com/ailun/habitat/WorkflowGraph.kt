package com.ailun.habitat

import com.google.gson.annotations.SerializedName

class WorkflowGraph {
    var trigger: Map<String, Any>? = null

    @SerializedName("start_node_id")
    var startNodeId: String? = null

    var nodes: Map<String, WorkflowNode>? = null

    // ── v2 fields (all nullable for backward compat) ──

    /** Optional workflow name. */
    var name: String? = null

    /** Optional workflow description. */
    var description: String? = null

    /** Workflow-level success criteria (Phase 2). */
    @SerializedName("success_criteria")
    var successCriteria: Map<String, Any>? = null

    /** Declared capabilities for this workflow (Phase 1). */
    var capabilities: List<String>? = null

    /** 将原始 trigger map 转换为 [TriggerConfig]，无 trigger 则返回 null。 */
    fun triggerConfig(): TriggerConfig? {
        val raw = trigger ?: return null
        val type = raw["type"]?.toString() ?: return null
        val params = raw.filterKeys { it != "type" }
        return TriggerConfig(type, params)
    }

    /**
     * Fast structural validation using the comprehensive graph verifier.
     * For handler registration checks, use [validateWithFactory] instead.
     */
    fun validate() {
        val result = com.ailun.habitat.graph.GraphVerifier(
            expressionEngine = com.ailun.habitat.expression.ExpressionEngine(),
            riskEngine = com.ailun.habitat.capability.RiskEngine(),
        ).verify(this)
        if (!result.isValid) {
            throw IllegalArgumentException(
                result.errors.joinToString("\n") { it.message }
            )
        }
    }

    /** Validate with handler registration checks against [factory]. */
    fun validateWithFactory(factory: NodeHandlerFactory) {
        val result = com.ailun.habitat.graph.GraphVerifier(
            expressionEngine = com.ailun.habitat.expression.ExpressionEngine(),
            riskEngine = com.ailun.habitat.capability.RiskEngine(),
            factory = factory,
        ).verify(this)
        if (!result.isValid) {
            throw IllegalArgumentException(
                result.errors.joinToString("\n") { it.message }
            )
        }
    }
}
