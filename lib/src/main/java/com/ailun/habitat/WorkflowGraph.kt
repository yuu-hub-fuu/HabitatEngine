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
     * Fast structural validation only — checks that nodes exist, start_node_id exists,
     * and every node has id and type.
     *
     * @deprecated Use [com.ailun.habitat.graph.GraphVerifier.verify] for comprehensive
     * static analysis (edge checks, reachability, variable analysis, risk assessment).
     * This method is preserved for backward compatibility and fast-fail checks.
     */
    @Deprecated(
        message = "Use GraphVerifier.verify() for comprehensive static analysis",
        replaceWith = ReplaceWith("graphVerifier.verify(graph)"),
    )
    fun validate() {
        val nodeMap = nodes ?: throw IllegalArgumentException("缺少 'nodes' 字段")
        if (nodeMap.isEmpty()) throw IllegalArgumentException("'nodes' 不能为空")

        val startId = startNodeId
            ?: throw IllegalArgumentException("缺少 'start_node_id' 字段")
        if (startId !in nodeMap) {
            throw IllegalArgumentException("start_node_id '$startId' 在 nodes 中不存在")
        }

        for ((key, node) in nodeMap) {
            if (node.id.isNullOrBlank()) {
                throw IllegalArgumentException("节点 '$key' 缺少 'id' 字段")
            }
            if (node.type.isNullOrBlank()) {
                throw IllegalArgumentException("节点 '${node.id}' 缺少 'type' 字段")
            }
        }
    }
}
