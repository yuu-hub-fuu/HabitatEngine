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
