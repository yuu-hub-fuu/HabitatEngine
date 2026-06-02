package com.ailun.habitat.graph

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.planir.CompilationResult

class HierarchicalCompiler(
    private val graphVerifier: GraphVerifier? = null,
) {
    /**
     * Compiles a hierarchical graph to a flat WorkflowGraph.
     *
     * Phases:
     * 1. Expand SubGraphRefs inline (prefix-scoped node IDs, variable remapping)
     * 2. Expand SkillRefs (placeholder — expands to ACTION_CALL_SKILL at runtime)
     * 3. Expand MacroRefs (text-level substitution in node params)
     * 4. Emit GuardDefs as CONDITION_SWITCH nodes before guarded sections
     */
    suspend fun compile(hierarchical: HierarchicalGraph): CompilationResult {
        val warnings = mutableListOf<String>()
        val allNodes = mutableMapOf<String, WorkflowNode>()
        val nodeMapping = mutableMapOf<String, String>()
        var counter = 0

        // Phase 1: Expand subgraphs inline
        val expandedSubGraphs = mutableMapOf<String, WorkflowNode>()
        for ((subId, subRef) in hierarchical.subGraphs) {
            val prefix = "sub_${subId}_"
            val subNodes = subRef.graph.nodes ?: continue
            for ((nodeId, node) in subNodes) {
                val newId = "$prefix$nodeId"
                val newNode = WorkflowNode().apply {
                    id = newId; type = node.type; label = node.label
                    description = node.description
                    // Remap params to apply inputMapping
                    params = node.params?.mapKeys { (k, _) ->
                        subRef.inputMapping[k] ?: subRef.inputMapping.entries
                            .firstOrNull { it.value == k }?.key ?: k
                    }?.mapValues { (_, v) ->
                        if (v is String) v else v
                    }
                    // Remap next/branches
                    next = node.next?.let { "$prefix$it" }
                    branches = node.branches?.mapKeys { (k, _) -> k }
                        ?.mapValues { (_, v) -> v?.let { "$prefix$it" } }
                }
                expandedSubGraphs[newId] = newNode
                nodeMapping[subId] = prefix
            }
        }
        allNodes.putAll(expandedSubGraphs)

        // Phase 2: Process HierarchicalNodes — expand subgraph/skill/macro refs
        var firstNode: String? = null
        for ((nodeId, hNode) in hierarchical.nodes) {
            val flatNode = when (hNode.type) {
                "subgraph" -> {
                    val ref = hierarchical.subGraphs[hNode.refId ?: ""]
                    if (ref != null) {
                        // Already expanded above; reference by prefix
                        nodeMapping[nodeId] = "sub_${hNode.refId}_"
                        null // No new flat node — reference is inline
                    } else {
                        warnings.add("Subgraph '${hNode.refId}' not found")
                        WorkflowNode().apply { id = nodeId; type = "ACTION_LOG"
                            params = mapOf("message" to "Subgraph not found: ${hNode.refId}", "level" to "warn")
                        }
                    }
                }
                "skill" -> {
                    val skillId = hNode.refId ?: ""
                    WorkflowNode().apply {
                        id = nodeId; type = "ACTION_CALL_SKILL"; label = hNode.label ?: "Skill: $skillId"
                        params = mapOf("skill_id" to skillId) + (hNode.params ?: emptyMap())
                        next = hNode.next; branches = hNode.branches
                    }
                }
                "macro" -> {
                    val macroRef = hierarchical.macros[hNode.refId ?: ""]
                    if (macroRef != null) {
                        val macroNode = WorkflowNode().apply {
                            id = nodeId; type = hNode.params?.get("type")?.toString() ?: "ACTION_LOG"
                            label = hNode.label; description = hNode.description
                            // Apply macro param substitution
                            params = hNode.params?.mapValues { (_, v) ->
                                val s = v.toString()
                                macroRef.paramOverrides.entries.fold(s) { acc, (k, value) ->
                                    acc.replace("\${$k}", value)
                                }
                            }
                            next = hNode.next; branches = hNode.branches
                        }
                        nodeMapping[nodeId] = nodeId
                        macroNode
                    } else {
                        warnings.add("Macro '${hNode.refId}' not found")
                        WorkflowNode().apply { id = nodeId; type = "ACTION_LOG"
                            params = mapOf("message" to "Macro not found: ${hNode.refId}", "level" to "warn")
                        }
                    }
                }
                "guard" -> {
                    val guard = hierarchical.guards[hNode.refId ?: ""]
                    if (guard != null) {
                        WorkflowNode().apply {
                            id = nodeId; type = "CONDITION_SWITCH"; label = "Guard: ${guard.description.take(30)}"
                            params = mapOf("expression" to guard.expression)
                            branches = mapOf("true" to hNode.next, "false" to guard.onViolation)
                        }
                    } else null
                }
                else -> {
                    // Plain node — pass through
                    WorkflowNode().apply {
                        id = nodeId; type = hNode.params?.get("type")?.toString() ?: hNode.type
                        label = hNode.label; description = hNode.description
                        params = hNode.params
                        next = hNode.next; branches = hNode.branches
                    }
                }
            }
            if (flatNode != null) allNodes[nodeId] = flatNode
            if (firstNode == null && hNode.id == hierarchical.startNodeId) firstNode = nodeId
        }

        // Phase 3: Emit guards that are referenced but not inline
        for ((guardId, guard) in hierarchical.guards) {
            if (guardId !in allNodes) {
                allNodes[guardId] = WorkflowNode().apply {
                    id = guardId; type = "CONDITION_SWITCH"; label = "Guard: ${guard.description.take(30)}"
                    params = mapOf("expression" to guard.expression)
                    branches = mapOf("true" to null, "false" to guard.onViolation)
                }
            }
        }

        val graph = WorkflowGraph().apply {
            startNodeId = firstNode ?: hierarchical.startNodeId
            nodes = allNodes.toMap()
            name = hierarchical.name; description = hierarchical.description
            capabilities = hierarchical.requiredCapabilities
        }

        // Verify
        if (graphVerifier != null) {
            val result = graphVerifier.verify(graph)
            if (!result.isValid) {
                warnings.addAll(result.errors.map { it.message })
            }
        }

        return CompilationResult(
            graph = graph,
            warnings = warnings,
            nodeMapping = nodeMapping,
        )
    }
}
