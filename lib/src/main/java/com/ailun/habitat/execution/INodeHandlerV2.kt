package com.ailun.habitat.execution

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * V2 handler interface — returns rich [NodeResult] instead of bare [String?].
 *
 * Extends [INodeHandler] so V2 handlers can be registered in [NodeHandlerFactory]
 * alongside V1 handlers. The V1 [handle] method delegates to V2 by default
 * via [LegacyHandlerAdapter], but V2 handlers can override V1 for efficiency.
 *
 * V2 advantages over V1:
 * - Explicit error signaling with rollback and compensation
 * - Variable change tracking for trajectory recording
 * - Risk event reporting for safety monitoring
 */
interface INodeHandlerV2 : INodeHandler {
    suspend fun handleV2(node: WorkflowNode, context: WorkflowContext): NodeResult

    /** Default V1 bridge — adapts V2 result back to V1 String? protocol. */
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val result = handleV2(node, context)
        return if (result.success) result.nextNodeId else null
    }
}
