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
 */
class LegacyHandlerAdapter(
    private val legacy: INodeHandler,
) : INodeHandlerV2 {

    override suspend fun handleV2(
        node: WorkflowNode,
        context: WorkflowContext,
    ): NodeResult {
        return try {
            val nextId = legacy.handle(node, context)
            NodeResult.next(nextId)
        } catch (e: CancellationException) {
            throw e // Preserve cancellation
        } catch (e: Exception) {
            NodeResult.error(e.message ?: "Handler error: ${legacy.javaClass.simpleName}")
        }
    }
}
