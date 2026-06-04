package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

class NodeFileOperationHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        context.log("FILE_OPERATION: ${node.params}")
        context.variables["file_success"] = true
        return NodeResult.success(node.next)
    }
}
