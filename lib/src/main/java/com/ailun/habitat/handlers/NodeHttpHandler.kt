package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

class NodeHttpHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        context.log("HTTP_REQUEST: ${node.params}")
        context.variables["http_success"] = true
        context.variables["http_status_code"] = 200
        return NodeResult.success(node.next)
    }
}
