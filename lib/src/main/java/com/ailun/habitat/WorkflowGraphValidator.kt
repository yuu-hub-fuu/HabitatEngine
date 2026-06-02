package com.ailun.habitat

enum class WorkflowValidationSeverity { ERROR, WARNING }

data class WorkflowValidationIssue(
    val severity: WorkflowValidationSeverity,
    val message: String,
    val nodeId: String? = null,
)

data class WorkflowValidationResult(
    val issues: List<WorkflowValidationIssue>,
) {
    val errors: List<WorkflowValidationIssue> = issues.filter { it.severity == WorkflowValidationSeverity.ERROR }
    val warnings: List<WorkflowValidationIssue> = issues.filter { it.severity == WorkflowValidationSeverity.WARNING }
    val isValid: Boolean = errors.isEmpty()

    fun requireValid() {
        if (!isValid) {
            val text = errors.joinToString("; ") { issue ->
                issue.nodeId?.let { "node '$it': ${issue.message}" } ?: issue.message
            }
            throw IllegalArgumentException(text)
        }
    }
}

object WorkflowGraphValidator {
    fun validate(
        graph: WorkflowGraph,
        registeredTypes: Set<String>? = null,
    ): WorkflowValidationResult {
        val issues = mutableListOf<WorkflowValidationIssue>()
        val nodes = graph.nodes
        if (nodes.isNullOrEmpty()) {
            issues += error("missing or empty nodes")
            return WorkflowValidationResult(issues)
        }

        val startId = graph.startNodeId?.trim()
        if (startId.isNullOrEmpty()) issues += error("missing start_node_id")
        else if (!nodes.containsKey(startId)) issues += error("start_node_id '$startId' does not exist")

        for ((key, node) in nodes) {
            val id = node.id?.trim()
            val type = node.type?.trim()
            if (id.isNullOrEmpty()) issues += error("missing id", key)
            else if (id != key) issues += error("id '$id' does not match key '$key'", key)
            if (type.isNullOrEmpty()) issues += error("missing type", key)
            else if (registeredTypes != null && type !in registeredTypes) issues += error("unregistered node type '$type'", key)
            validateEdges(key, node, nodes, issues)
        }

        if (startId != null && nodes.containsKey(startId)) {
            validateReachability(startId, nodes, issues)
            validateCycles(startId, nodes, issues)
        }
        return WorkflowValidationResult(issues)
    }

    private fun validateEdges(
        key: String,
        node: WorkflowNode,
        nodes: Map<String, WorkflowNode>,
        issues: MutableList<WorkflowValidationIssue>,
    ) {
        node.next?.trim()?.let { next ->
            if (next.isEmpty()) issues += warning("empty next is treated as stop", key)
            else if (!nodes.containsKey(next)) issues += error("next points to missing node '$next'", key)
        }
        node.branches.orEmpty().forEach { (branch, targetRaw) ->
            val target = targetRaw?.trim()
            if (!target.isNullOrEmpty() && !nodes.containsKey(target)) {
                issues += error("branch '$branch' points to missing node '$target'", key)
            }
        }
        val type = node.type?.trim()
        if ((type == NodeHandlerFactory.CONDITION_SWITCH || type == NodeHandlerFactory.CONDITION_ADVANCED_SWITCH) && node.branches.isNullOrEmpty()) {
            issues += warning("condition node has no branches", key)
        }
    }

    private fun validateReachability(
        startId: String,
        nodes: Map<String, WorkflowNode>,
        issues: MutableList<WorkflowValidationIssue>,
    ) {
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!seen.add(id)) continue
            successorsOf(nodes[id]).forEach { next -> if (next in nodes && next !in seen) queue.add(next) }
        }
        nodes.keys.filter { it !in seen }.sorted().forEach { issues += warning("unreachable node", it) }
    }

    private fun validateCycles(
        startId: String,
        nodes: Map<String, WorkflowNode>,
        issues: MutableList<WorkflowValidationIssue>,
    ) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val reported = mutableSetOf<String>()
        fun dfs(id: String) {
            if (id in visiting) {
                if (reported.add(id)) issues += warning("cycle detected; maxSteps will cap execution", id)
                return
            }
            if (id in visited) return
            visiting += id
            successorsOf(nodes[id]).forEach { next -> if (next in nodes) dfs(next) }
            visiting -= id
            visited += id
        }
        dfs(startId)
    }

    private fun successorsOf(node: WorkflowNode?): List<String> {
        if (node == null) return emptyList()
        val out = mutableListOf<String>()
        node.next?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        node.branches.orEmpty().values.forEach { it?.trim()?.takeIf { value -> value.isNotEmpty() }?.let { value -> out += value } }
        return out.distinct()
    }

    private fun error(message: String, nodeId: String? = null) = WorkflowValidationIssue(WorkflowValidationSeverity.ERROR, message, nodeId)
    private fun warning(message: String, nodeId: String? = null) = WorkflowValidationIssue(WorkflowValidationSeverity.WARNING, message, nodeId)
}
