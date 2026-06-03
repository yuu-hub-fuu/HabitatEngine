package com.ailun.habitat.handlers

import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.execution.INodeHandlerV2
import com.ailun.habitat.execution.NodeResult

/**
 * V2 transactional Try-Catch handler with state snapshots.
 *
 * Works as a two-visit checkpoint:
 * - **Visit 1 (entry):** Saves a variable snapshot to `_try_snap_{nodeId}`,
 *   sets `_try_phase_{nodeId}=enter`, and routes to `try_node`.
 * - **Visit 2 (checkpoint):** `try_node` and its successors should route back
 *   to this handler via `next`. On this visit the handler reads `_last_error`:
 *     - If error: restores the entry snapshot, stores the error in `catch_var`,
 *       and routes to `branches["error"]`.
 *     - If success: clears the snapshot and routes to `branches["success"]`.
 *
 * params:
 * - `try_node`: Node ID to execute (defaults to `node.next` if missing).
 * - `catch_var`: Variable name to store the error message (default `exception_msg`).
 *
 * JSON example:
 * ```
 * {
 *   "id": "try_catch",
 *   "type": "ACTION_TRY_CATCH",
 *   "params": { "try_node": "risky_action", "catch_var": "error_msg" },
 *   "next": "try_catch",      // try_node must route back here after execution
 *   "branches": { "success": "after_ok", "error": "on_fail" }
 * }
 * ```
 */
class NodeTryCatchHandlerV2 : INodeHandlerV2 {

    override suspend fun handleV2(
        node: WorkflowNode,
        context: WorkflowContext,
    ): NodeResult {
        val nodeId = node.id?.trim().orEmpty()
        val catchVar = node.params?.get("catch_var")?.toString()?.takeIf { it.isNotEmpty() } ?: "exception_msg"
        val tryNodeId = node.params?.get("try_node")?.toString()?.takeIf { it.isNotEmpty() }
        val phaseKey = "_try_phase_$nodeId"
        val snapKey = "_try_snap_$nodeId"

        val phase = context.variables[phaseKey]?.toString()

        if (phase == "enter") {
            // ── Visit 2: checkpoint (try-node completed, flow returned) ──
            context.variables.remove(phaseKey)

            val hasError = context.variables["_last_error"] == true
            if (hasError) {
                val errorMsg = context.variables["_last_error_msg"]?.toString() ?: "Unknown error"
                context.variables[catchVar] = errorMsg
                context.log("TryCatch($nodeId): error detected — '$errorMsg'; restoring snapshot")

                // Restore pre-try variable state.
                @Suppress("UNCHECKED_CAST")
                val snap = context.variables[snapKey] as? Map<String, Any?>
                if (snap != null) {
                    // Remove any variables added since entry (not present in snap).
                    val addedKeys = context.variables.keys - snap.keys
                    addedKeys.forEach { context.variables.remove(it) }
                    // Restore original values.
                    snap.forEach { (k, v) -> context.variables[k] = v }
                    context.variables.remove(snapKey)
                    context.log("TryCatch($nodeId): snapshot restored (${snap.size} vars)")
                }

                // Keep error context for downstream handlers.
                context.variables["_last_error"] = true
                context.variables["_last_error_msg"] = errorMsg

                val errorBranch = node.branches?.get("error")?.trim()
                    ?.takeIf { it.isNotEmpty() }
                if (errorBranch != null) {
                    return NodeResult.error(errorMsg)
                        .copy(nextNodeId = errorBranch)
                }
                return NodeResult.error(errorMsg)
            }

            // Success — just clean up and route.
            context.variables.remove(snapKey)
            context.log("TryCatch($nodeId): no error — routing to success branch")
            return NodeResult.fromBranch(node, "success")
        }

        // ── Visit 1: entry — save snapshot and launch try-node ──
        context.log("TryCatch($nodeId): entering try block, snapshotted ${context.variables.size} vars")

        // Deep-copy the current variable state.
        val snap = HashMap(context.variables.toMap())
        context.variables[snapKey] = snap
        context.variables[phaseKey] = "enter"

        // Pre-clear error state so try-node starts fresh.
        context.variables["_last_error"] = false
        context.variables["_last_error_msg"] = ""

        val nextId = tryNodeId ?: node.next?.trim()?.takeIf { it.isNotEmpty() }
        return NodeResult.next(nextId)
    }
}
