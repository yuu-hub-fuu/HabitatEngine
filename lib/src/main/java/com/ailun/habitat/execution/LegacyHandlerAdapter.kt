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
 * **V1 failure detection:**
 * Many V1 handlers signal failure by setting convention variables
 * (e.g. `file_success=false`, `http_success=false`) and then returning `node.next`.
 * This adapter snapshots those convention variables BEFORE execution and only
 * treats a variable as a "new" failure if it was NOT `false` beforehand. This
 * prevents stale failure vars from earlier nodes from polluting the current node.
 */
class LegacyHandlerAdapter(
    private val legacy: INodeHandler,
) : INodeHandlerV2 {

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
        // Snapshot pre-execution state so we only credit new failures to this handler.
        val prevLastError = context.variables["_last_error"]
        val prevFailureSnapshot = FAILURE_VARIABLES.associateWith { context.variables[it] }

        return try {
            val nextId = legacy.handle(node, context)

            // Detect NEW failures introduced by this handler (not left over from prior nodes).
            var failedVar: String? = null

            // _last_error: treat as new only if it changed from anything else to true
            if (context.variables["_last_error"] == true && prevLastError != true) {
                failedVar = "_last_error"
            }

            // Convention vars: treat as new failure only if it went from non-false to false
            if (failedVar == null) {
                for (name in FAILURE_VARIABLES) {
                    val prev = prevFailureSnapshot[name]
                    val now = context.variables[name]
                    if (now == false && prev != false) {
                        failedVar = name
                        break
                    }
                }
            }

            if (failedVar != null) {
                val msg = context.variables["_last_error_msg"]?.toString()
                    ?: "Handler '${legacy.javaClass.simpleName}' signalled failure via '$failedVar=false'"
                NodeResult.error(msg)
            } else {
                NodeResult.next(nextId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NodeResult.error(e.message ?: "Handler error: ${legacy.javaClass.simpleName}")
        }
    }
}
