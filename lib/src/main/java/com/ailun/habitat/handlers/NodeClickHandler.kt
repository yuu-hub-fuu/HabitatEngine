package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

class NodeClickHandler(
    private val a11yProvider: com.ailun.habitat.api.IAccessibilityProvider? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        context.log("CLICK: target=${node.params?.get("target")}")
        context.variables["click_success"] = true
        return NodeResult.success(node.next)
    }
}
