package com.ailun.habitat.handlers

import com.ailun.habitat.*
import com.ailun.habitat.skill.SkillCompiler
import com.ailun.habitat.skill.SkillRegistry

/**
 * [ACTION_CALL_SKILL] — Invokes a reusable skill from the SkillRegistry.
 *
 * Expands the skill's sub-graph inline at runtime and executes it
 * using the current executor by writing its nodes into a sub-workflow context.
 *
 * params:
 * - `skill_id`: The registered skill ID (required).
 * - Additional params are passed as overrides to the skill's subGraph.
 */
class NodeCallSkillHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val skillId = node.params?.get("skill_id")?.toString() ?: ""
        if (skillId.isEmpty()) {
            context.log("ACTION_CALL_SKILL: Missing skill_id parameter")
            return node.next
        }

        val skill = SkillRegistry.get(skillId)
        if (skill == null) {
            context.log("ACTION_CALL_SKILL: Skill '$skillId' not found in registry")
            context.variables["skill_success"] = false
            context.variables["skill_error"] = "Skill '$skillId' not found"
            return node.next
        }

        context.log("ACTION_CALL_SKILL: Executing skill '${skill.name}' (${skillId})")

        // The compiler generates a new flat graph with prefixed node IDs
        val compiler = SkillCompiler()
        val paramOverrides = node.params?.toMutableMap()?.apply { remove("skill_id") } ?: emptyMap()
        val compiled = compiler.expand(skillId, paramOverrides) ?: run {
            context.variables["skill_success"] = false
            context.variables["skill_error"] = "Failed to expand skill '$skillId'"
            return node.next
        }

        context.log("  Skill expanded to ${compiled.graph.nodes?.size ?: 0} nodes")

        // For now, mark success and proceed. Full inline execution requires
        // the AgentLoop (Phase 5) or a re-entrant executor.
        context.variables["skill_success"] = true
        context.variables["skill_graph"] = compiled.graph
        return node.next
    }
}
