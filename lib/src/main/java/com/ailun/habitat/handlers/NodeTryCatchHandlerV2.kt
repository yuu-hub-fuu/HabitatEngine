package com.ailun.habitat.handlers

import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.execution.INodeHandlerV2
import com.ailun.habitat.execution.NodeResult

/**
 * V2 transactional Try-Catch handler.
 *
 * Unlike the V1 [NodeTryCatchHandler] which only reads pre-existing error state,
 * this handler actually executes the try-node and routes based on the result.
 *
 * params:
 * - `try_node`: Node ID to execute (required).
 * - `catch_var`: Variable name to store the error message (default "exception_msg").
 *
 * Execution:
 * 1. The executor delegate is called to run the try-node inline.
 * 2. If the try-node returns success: route to branches["success"].
 * 3. If the try-node fails: store error in catch_var, route to branches["error"].
 */
class NodeTryCatchHandlerV2(
    private val tryBlockExecutor: (suspend (String) -> NodeResult)? = null,
) : INodeHandlerV2 {

    override suspend fun handleV2(
        node: WorkflowNode,
        context: WorkflowContext,
    ): NodeResult {
        val tryNodeId = node.params?.get("try_node")?.toString()
            ?: return NodeResult.error("try_node parameter missing")

        val catchVar = node.params?.get("catch_var")?.toString() ?: "exception_msg"

        context.log("TryCatch: executing try node '$tryNodeId'")

        if (tryBlockExecutor == null) {
            // No inline executor — fall back to V1-style error state reading
            val hasError = context.getVariable("_last_error") == true
            if (hasError) {
                val errorMsg = context.getVariable("_last_error_msg")?.toString() ?: "Unknown error"
                context.variables[catchVar] = errorMsg
                context.variables["_last_error"] = false
                context.variables["_last_error_msg"] = ""
                context.log("TryCatch: error detected — '$errorMsg'")
                return NodeResult.fromBranch(node, "error")
            }
            context.log("TryCatch: no error — continuing")
            return NodeResult.fromBranch(node, "success")
        }

        // Execute the try block inline
        val tryResult = tryBlockExecutor(tryNodeId)

        return if (tryResult.success) {
            context.log("TryCatch: try block succeeded")
            NodeResult.fromBranch(node, "success")
        } else {
            val errorMsg = tryResult.error ?: "Try block failed"
            context.variables[catchVar] = errorMsg
            context.variables["_last_error"] = true
            context.variables["_last_error_msg"] = errorMsg
            context.log("TryCatch: try block failed — '$errorMsg'")
            NodeResult.error(errorMsg)
                .copy(nextNodeId = node.branches?.get("error"))
        }
    }
}
