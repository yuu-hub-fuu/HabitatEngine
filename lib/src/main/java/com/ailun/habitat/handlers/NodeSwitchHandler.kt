package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

class NodeSwitchHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val varName = node.params?.get("variable")?.toString() ?: ""
        val actual = context.getVariable(varName)?.toString() ?: ""
        context.log("SWITCH: $varName=$actual")
        val matched = node.branches?.get(actual) ?: node.branches?.get("default")
        return NodeResult.success(matched)
    }
}
