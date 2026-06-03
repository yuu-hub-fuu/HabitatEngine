package com.ailun.habitat

interface INodeHandler {
    suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult
}
