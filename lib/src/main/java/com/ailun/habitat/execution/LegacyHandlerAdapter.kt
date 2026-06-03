package com.ailun.habitat.execution

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.CancellationException

/**
 * Wraps a V1 [INodeHandler] as a V2 [INodeHandlerV2].
 *
 * Used by [com.ailun.habitat.HabitatExecutor] to uniformly process both V1 and V2
 * handlers through the NodeResult-based execution pipeline.
 *
 * For direct V2 handlers, the executor calls [handleV2] directly.
 * V1 handlers are wrapped here; their String? return is converted to NodeResult.
 *
 * **V1 failure detection:**
 * Many V1 handlers signal failure by setting convention variables
 * (e.g. `file_success=false`, `http_success=false`) and then returning `node.next`.
 * Without explicit error signaling this would be misclassified as success by the V2
 * pipeline, making error branches unreachable. This adapter inspects those convention
 * variables after every V1 call and converts soft failures to [NodeResult.error].
 */
class LegacyHandlerAdapter(
    private val legacy: INodeHandler,
) : INodeHandlerV2 {

    /** Convention variable names that V1 handlers use to signal failure. */
    companion object {
        private val FAILURE_VARIABLES = setOf(
            "file_success", "http_success", "shell_success", "sms_success",
            "sms_found", "screenshot_success", "wifi_success", "bluetooth_success",
            "call_success", "base64_success", "clipboard_success", "force_stop_success",
            "foreground_success", "skill_success", "confirmation_approved",
            "app_search_success", "element_found", "notification_success",
            "get_time_success", "random_success", "math_success",
        )
    }

    override suspend fun handleV2(
        node: WorkflowNode,
        context: WorkflowContext,
    ): NodeResult {
        return try {
            val nextId = legacy.handle(node, context)
            val failedVar = detectV1Failure(context)
            if (failedVar != null) {
                val msg = context.variables["_last_error_msg"]?.toString()
                    ?: "Handler '${legacy.javaClass.simpleName}' signalled failure via '$failedVar=false'"
                NodeResult.error(msg)
            } else {
                NodeResult.next(nextId)
            }
        } catch (e: CancellationException) {
            throw e // Preserve cancellation
        } catch (e: Exception) {
            NodeResult.error(e.message ?: "Handler error: ${legacy.javaClass.simpleName}")
        }
    }

    /**
     * Scan the context for any convention variable that signals a V1 soft-failure.
     * Returns the name of the first failure flag found, or null if all are success/absent.
     */
    private fun detectV1Failure(context: WorkflowContext): String? {
        // Primary signal — newer handlers and the executor itself set this.
        if (context.variables["_last_error"] == true) return "_last_error"

        // Scan known convention variables.
        for (name in FAILURE_VARIABLES) {
            if (context.variables[name] == false) return name
        }
        return null
    }
}
