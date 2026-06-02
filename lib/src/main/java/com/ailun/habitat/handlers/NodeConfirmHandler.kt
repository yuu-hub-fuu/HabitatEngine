package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.confirmation.ConfirmationManager

/**
 * [ACTION_CONFIRM] — Pauses workflow execution until the user explicitly approves
 * the next dangerous action.
 *
 * This handler is auto-inserted by compilers (PlanIRCompiler, HierarchicalCompiler)
 * before any node whose risk level >= HIGH. It can also be manually placed in
 * flat JSON workflows.
 *
 * params:
 * - `message`: Override the confirmation message (optional).
 * - `target_node`: The node ID that requires confirmation (for logging).
 *
 * Execution:
 * 1. Reads the pre-generated token from `_confirm_token` in params.
 * 2. Calls ConfirmationManager.ensureConfirmed().
 * 3. If approved: proceeds to `next` with the token consumed.
 * 4. If denied: routes to `branches["denied"]` or stops the workflow.
 *
 * Example:
 * ```json
 * {
 *   "id": "confirm1",
 *   "type": "ACTION_CONFIRM",
 *   "params": {
 *     "message": "About to delete files at /sdcard/temp",
 *     "target_node": "delete_files"
 *   },
 *   "next": "delete_files",
 *   "branches": { "denied": "clean_exit" }
 * }
 * ```
 */
class NodeConfirmHandler(
    private val confirmationManager: ConfirmationManager? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        // If no confirmation manager, skip (dev/test mode)
        if (confirmationManager == null) {
            context.log("ACTION_CONFIRM: No ConfirmationManager — auto-approving in dev mode")
            return node.next
        }

        val customMessage = node.params?.get("message")?.toString()
        val targetNode = node.params?.get("target_node")?.toString() ?: node.next ?: "next"

        if (customMessage != null) {
            context.log("ACTION_CONFIRM: $customMessage")
        }
        context.log("ACTION_CONFIRM: Waiting for user confirmation for '$targetNode'...")

        val interpolatedParams = mutableMapOf<String, String>()
        node.params?.forEach { (k, v) ->
            val raw = v?.toString() ?: ""
            interpolatedParams[k] = context.interpolate(raw)
        }

        // Build a synthetic node representing the action being confirmed
        val confirmed = confirmationManager.ensureConfirmed(node, interpolatedParams)

        return if (confirmed) {
            context.log("ACTION_CONFIRM: User approved — proceeding to $targetNode")
            context.variables["confirmation_approved"] = true
            node.next
        } else {
            context.log("ACTION_CONFIRM: User DENIED — routing to denied branch or stopping")
            context.variables["confirmation_approved"] = false
            node.branches?.get("denied")
        }
    }
}
