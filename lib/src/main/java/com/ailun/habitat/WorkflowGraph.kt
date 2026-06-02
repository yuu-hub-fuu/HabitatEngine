package com.ailun.habitat

import com.google.gson.annotations.SerializedName

class WorkflowGraph {
    var trigger: Map<String, Any>? = null

    @SerializedName("start_node_id")
    var startNodeId: String? = null

    var nodes: Map<String, WorkflowNode>? = null

    /** 将原始 trigger map 转换为 [TriggerConfig]，无 trigger 则返回 null。 */
    fun triggerConfig(): TriggerConfig? {
        val raw = trigger ?: return null
        val type = raw["type"]?.toString() ?: return null
        val params = raw.filterKeys { it != "type" }
        return TriggerConfig(type, params)
    }

    /**
     * Compatibility entry point used by HabitatJson. Full runtime validation is
     * performed by HabitatExecutor with the actual NodeHandlerFactory registry.
     */
    fun validate() {
        WorkflowGraphValidator.validate(this).requireValid()
    }
}
