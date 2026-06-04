package com.ailun.habitat.handlers

import com.ailun.habitat.*
import com.ailun.habitat.skill.SkillCompiler
import com.ailun.habitat.skill.SkillRegistry
import kotlinx.coroutines.CancellationException

/**
 * [ACTION_CALL_SKILL] — Invokes a reusable skill (sub-graph) from the SkillRegistry.
 *
 * Expands the skill's sub-graph at runtime and executes it in an **isolated context**.
 * Only declared output variables are merged back into the parent context.
 *
 * params:
 * - `skill_id`: The registered skill ID (required).
 * - `outputs` (optional): Comma-separated list of variable names to export from
 *   the skill's context back to the parent. If empty, all variables are merged
 *   (backward-compatible mode).
 * - Additional params are passed as variable overrides to the skill's subGraph.
 *
 * NOTE: SkillRegistry is currently a simple name→graph map. Future versions should
 * add versioning, entry conditions, success criteria, and failure examples.
 *
 * @param subGraphExecutor Runs a sub-workflow graph within an isolated context.
 */
class NodeCallSkillHandler(
    private val subGraphExecutor: (suspend (WorkflowGraph, WorkflowContext) -> Unit)? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val skillId = node.params?.get("skill_id")?.toString().orEmpty()
        if (skillId.isEmpty()) {
            context.log("ACTION_CALL_SKILL: Missing skill_id parameter")
            return NodeResult.failure(node.next, "Missing skill_id")
        }

        val skill = SkillRegistry.get(skillId)
        if (skill == null) {
            context.log("ACTION_CALL_SKILL: Skill '$skillId' not found in registry")
            return NodeResult.failure(
                next = node.branches?.get("error") ?: node.next,
                error = "Skill '$skillId' not found",
                vars = mapOf("skill_success" to false, "skill_error" to "Skill '$skillId' not found")
            )
        }

        context.log("ACTION_CALL_SKILL: Executing skill '${skill.name}' ($skillId)")

        val compiler = SkillCompiler()
        val paramOverrides = node.params?.toMutableMap()?.apply { remove("skill_id") } ?: emptyMap()
        val compiled = compiler.expand(skillId, paramOverrides)
        if (compiled == null) {
            return NodeResult.failure(
                next = node.branches?.get("error") ?: node.next,
                error = "Failed to expand skill '$skillId'",
                vars = mapOf("skill_success" to false, "skill_error" to "Failed to expand skill '$skillId'")
            )
        }

        val skillGraph = compiled.graph
        context.log("  Skill expanded to ${skillGraph.nodes?.size ?: 0} nodes")

        val executor = subGraphExecutor
        if (executor != null) {
            // ── Isolated sub-graph execution ──
            // Create a scoped context that:
            // 1. Inherits parent variables as read-only baseline
            // 2. Writes go to a local overlay
            // 3. On completion, only declared outputs merge back
            val skillCtx = WorkflowContext(
                context = context.appContext,
                definitionId = skillId,
                workflowId = "${context.workflowId}_skill_${skillId}",
                strictInterpolation = false,  // skills may reference parent vars
            ).apply {
                // Inherit parent variables as baseline
                variables.putAll(context.variables)
                onLog = context.onLog
            }

            try {
                executor(skillGraph, skillCtx)
                context.variables["skill_success"] = true
                context.log("  Skill '${skill.name}' completed successfully")

                // Merge declared outputs back to parent
                val outputKeys = node.params?.get("outputs")?.toString()
                    ?.split(",", ";")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }

                if (outputKeys.isNullOrEmpty()) {
                    // Backward-compatible: merge all skill variables back
                    skillCtx.variables.forEach { (k, v) ->
                        if (!k.startsWith("_")) {  // skip internal vars
                            context.variables[k] = v
                        }
                    }
                } else {
                    // Only merge declared outputs
                    for (key in outputKeys) {
                        context.variables[key] = skillCtx.variables[key]
                    }
                }
            } catch (_: CancellationException) {
                throw CancellationException("Skill '${skill.name}' cancelled")
            } catch (e: Exception) {
                context.log("  Skill '${skill.name}' failed: ${e.message}")
                return NodeResult.failure(
                    next = node.branches?.get("error") ?: node.next,
                    error = "Skill execution failed: ${e.message}",
                    vars = mapOf(
                        "skill_success" to false,
                        "skill_error" to "Skill execution failed: ${e.message}"
                    )
                )
            }
        } else {
            // Legacy fallback: mark as done but skip execution
            context.variables["skill_success"] = true
            context.variables["skill_graph"] = skillGraph
            context.log("  Skill '${skill.name}' expansion only (no sub-executor; execution skipped)")
        }
        return NodeResult.success(node.next)
    }
}
