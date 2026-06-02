package com.ailun.habitat.skill

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.planir.CompilationResult

class SkillCompiler {
    /**
     * Expands a skill reference into an inline flat graph.
     * Copies the skill's subGraph, remaps node IDs with prefix to avoid collisions,
     * applies parameter overrides.
     */
    fun expand(
        skillId: String,
        paramOverrides: Map<String, Any> = emptyMap(),
        idPrefix: String = "skill_",
    ): CompilationResult? {
        val skill = SkillRegistry.get(skillId) ?: return null

        val prefix = "$idPrefix${skill.id}_"
        val subNodes = skill.subGraph.nodes ?: return null
        val warnings = mutableListOf<String>()
        val allNodes = mutableMapOf<String, WorkflowNode>()

        for ((nodeId, node) in subNodes) {
            val newId = "$prefix$nodeId"
            val newNode = WorkflowNode().apply {
                id = newId
                type = node.type
                label = "[${skill.name}] ${node.label ?: node.type}"
                description = node.description

                // Apply param overrides and keep original params
                params = node.params?.toMutableMap()?.apply {
                    paramOverrides.forEach { (k, v) -> put(k, v) }
                }

                // Remap next/branches with prefix
                next = node.next?.let { if (it in subNodes) "$prefix$it" else it }
                branches = node.branches?.mapKeys { (k, _) -> k }
                    ?.mapValues { (_, v) -> v?.let { if (it in subNodes) "$prefix$it" else it } }
            }
            allNodes[newId] = newNode
        }

        val startId = "$prefix${skill.subGraph.startNodeId}"

        return CompilationResult(
            graph = WorkflowGraph().apply {
                startNodeId = startId
                nodes = allNodes.toMap()
                name = "Skill: ${skill.name}"
                description = "Expanded from skill '${skill.id}' v${skill.version}"
            },
            warnings = warnings,
        )
    }
}
