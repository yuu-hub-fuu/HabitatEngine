package com.ailun.habitat.handlers

import com.ailun.habitat.*
import com.ailun.habitat.skill.SkillCompiler
import com.ailun.habitat.skill.SkillRegistry
import kotlinx.coroutines.CancellationException

/**
 * [ACTION_CALL_SKILL] — Invokes a reusable skill from the SkillRegistry.
 *
 * Expands the skill's sub-graph at runtime and executes it inline using
 * the provided [subGraphExecutor]. If no executor is provided, the node
 * falls back to V1 behaviour (mark `skill_success=true` and skip).
 *
 * params:
 * - `skill_id`: The registered skill ID (required).
 * - Additional params are passed as overrides to the skill's subGraph.
 *
 * @param subGraphExecutor Optional callback that runs a sub-workflow graph
 *   within the calling workflow's context. When null, skill expansion is
 *   performed but execution is skipped (legacy mode).
 */
class NodeCallSkillHandler(
    private val subGraphExecutor: (suspend (WorkflowGraph, WorkflowContext) -> Unit)? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val skillId = node.params?.get("skill_id")?.toString().orEmpty()
        if (skillId.isEmpty()) {
            context.log("ACTION_CALL_SKILL: Missing skill_id parameter")
            return node.nextResult()
        }

        val skill = SkillRegistry.get(skillId)
        if (skill == null) {
            context.log("ACTION_CALL_SKILL: Skill '$skillId' not found in registry")
            context.variables["skill_success"] = false
            context.variables["skill_error"] = "Skill '$skillId' not found"
            return node.nextResult()
        }

        context.log("ACTION_CALL_SKILL: Executing skill '${skill.name}' ($skillId)")

        val compiler = SkillCompiler()
        val paramOverrides = node.params?.toMutableMap()?.apply { remove("skill_id") } ?: emptyMap()
        val compiled = compiler.expand(skillId, paramOverrides)
        if (compiled == null) {
            context.variables["skill_success"] = false
            context.variables["skill_error"] = "Failed to expand skill '$skillId'"
            return node.nextResult()
        }

        val skillGraph = compiled.graph
        context.log("  Skill expanded to ${skillGraph.nodes?.size ?: 0} nodes")

        val executor = subGraphExecutor
        if (executor != null) {
            try {
                // Run the skill sub-graph with the parent context so variable
                // changes are visible to subsequent nodes.
                executor(skillGraph, context)
                context.variables["skill_success"] = true
                context.log("  Skill '${skill.name}' completed successfully")
            } catch (_: CancellationException) {
                throw CancellationException("Skill '${skill.name}' cancelled")
            } catch (e: Exception) {
                context.variables["skill_success"] = false
                context.variables["skill_error"] = "Skill execution failed: ${e.message}"
                context.variables["_last_error"] = true
                context.variables["_last_error_msg"] = "Skill '${skill.name}': ${e.message}"
                context.log("  Skill '${skill.name}' failed: ${e.message}")
            }
        } else {
            // Legacy fallback: mark as done so workflows don't break,
            // but log clearly that execution was skipped.
            context.variables["skill_success"] = true
            context.variables["skill_graph"] = skillGraph
            context.log("  Skill '${skill.name}' expansion only (no sub-executor; execution skipped)")
        }
        return node.nextResult()
    }
}
