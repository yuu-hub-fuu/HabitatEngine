package com.ailun.habitat.execution

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.CancellationException

class LegacyHandlerAdapter(
    private val legacy: INodeHandler,
) : INodeHandlerV2 {

    override suspend fun handleV2(
        node: WorkflowNode,
        context: WorkflowContext,
    ): NodeResult {
        return try {
            legacy.handle(node, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NodeResult.failure(null, e.message ?: "Handler error: ${legacy.javaClass.simpleName}")
        }
    }
}
