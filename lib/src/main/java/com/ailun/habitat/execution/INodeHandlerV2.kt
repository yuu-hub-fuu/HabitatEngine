package com.ailun.habitat.execution

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * V2 handler interface — returns rich [NodeResult] instead of bare [String?].
 * Since INodeHandler now returns NodeResult natively, this is a thin extension point
 * for handlers that need extra metadata (risk events, output variables, etc.).
 */
interface INodeHandlerV2 : INodeHandler {
    suspend fun handleV2(node: WorkflowNode, context: WorkflowContext): NodeResult

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        return handleV2(node, context)
    }
}
