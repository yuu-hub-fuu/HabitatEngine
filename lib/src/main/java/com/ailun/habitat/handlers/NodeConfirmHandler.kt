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
 * 1. Calls ConfirmationManager.ensureConfirmed().
 * 2. If approved: proceeds to `next`.
 * 3. If denied or unavailable: routes to `branches["denied"]` or stops the workflow.
 */
class NodeConfirmHandler(
    private val confirmationManager: ConfirmationManager? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val customMessage = node.params?.get("message")?.toString()
        val targetNode = node.params?.get("target_node")?.toString() ?: node.next ?: "next"

        if (customMessage != null) {
            context.log("ACTION_CONFIRM: $customMessage")
        }

        val manager = confirmationManager
        if (manager == null) {
            context.log("ACTION_CONFIRM: no ConfirmationManager is available; denying by default")
            context.variables["confirmation_approved"] = false
            context.variables["_last_error"] = true
            context.variables["_last_error_msg"] = "ConfirmationManager unavailable for '$targetNode'"
            return node.branches?.get("denied")
        }

        context.log("ACTION_CONFIRM: Waiting for user confirmation for '$targetNode'...")

        val interpolatedParams = mutableMapOf<String, String>()
        node.params?.forEach { (k, v) ->
            val raw = v?.toString() ?: ""
            interpolatedParams[k] = context.interpolate(raw)
        }

        val confirmed = manager.ensureConfirmed(node, interpolatedParams)

        return if (confirmed) {
            context.log("ACTION_CONFIRM: User approved — proceeding to $targetNode")
            context.variables["confirmation_approved"] = true
            node.next
        } else {
            context.log("ACTION_CONFIRM: User denied — routing to denied branch or stopping")
            context.variables["confirmation_approved"] = false
            node.branches?.get("denied")
        }
    }
}
