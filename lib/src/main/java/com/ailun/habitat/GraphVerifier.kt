package com.ailun.habitat

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

object GraphVerifier {
    fun verify(graph: WorkflowGraph): GraphVerificationResult {
        val issues = mutableListOf<GraphIssue>()
        val nodes = graph.nodes

        if (nodes.isNullOrEmpty()) {
            issues += GraphIssue(GraphIssue.Level.ERROR, null, "缺少 nodes 或 nodes 为空")
            return GraphVerificationResult(issues)
        }

        val startId = graph.startNodeId
        if (startId.isNullOrBlank()) {
            issues += GraphIssue(GraphIssue.Level.ERROR, null, "缺少 start_node_id")
        } else if (!nodes.containsKey(startId)) {
            issues += GraphIssue(GraphIssue.Level.ERROR, startId, "start_node_id 不存在于 nodes")
        }

        for ((mapKey, node) in nodes) {
            if (node.id.isNullOrBlank()) {
                issues += GraphIssue(GraphIssue.Level.ERROR, mapKey, "节点缺少 id")
            } else if (node.id != mapKey) {
                issues += GraphIssue(GraphIssue.Level.WARNING, mapKey, "node.id 与 nodes 的 key 不一致")
            }

            if (node.type.isNullOrBlank()) {
                issues += GraphIssue(GraphIssue.Level.ERROR, mapKey, "节点缺少 type")
            }

            val next = node.next
            if (!next.isNullOrBlank() && !nodes.containsKey(next)) {
                issues += GraphIssue(GraphIssue.Level.ERROR, mapKey, "next 指向不存在的节点: $next")
            }

            node.branches?.forEach { (branch, target) ->
                if (!target.isNullOrBlank() && !nodes.containsKey(target)) {
                    issues += GraphIssue(GraphIssue.Level.ERROR, mapKey, "branch '$branch' 指向不存在的节点: $target")
                }
            }
        }

        val reachable = collectReachable(startId, nodes)
        for (id in nodes.keys) {
            if (id !in reachable) {
                issues += GraphIssue(GraphIssue.Level.WARNING, id, "不可达节点")
            }
        }

        return GraphVerificationResult(issues)
    }

    private fun collectReachable(startId: String?, nodes: Map<String, WorkflowNode>): Set<String> {
        if (startId.isNullOrBlank() || !nodes.containsKey(startId)) return emptySet()
        val visited = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.add(startId)

        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!visited.add(id)) continue
            val node = nodes[id] ?: continue

            node.next?.takeIf { it.isNotBlank() && nodes.containsKey(it) }?.let { stack.add(it) }
            node.branches?.values
                ?.filterNotNull()
                ?.filter { it.isNotBlank() && nodes.containsKey(it) }
                ?.forEach { stack.add(it) }
        }

        return visited
    }
}
