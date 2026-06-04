package com.ailun.habitat.handlers

import android.widget.Toast
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ToastNodeHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawMessage = node.params?.get("message")?.toString().orEmpty()
        val message = context.interpolate(rawMessage)
        try {
            withContext(Dispatchers.Main) {
                Toast.makeText(context.appContext, message, Toast.LENGTH_SHORT).show()
            }
            context.log("Toast: $message")
        } catch (_: Exception) {
            context.log("Toast FAILED (no UI context): $message")
        }
        return NodeResult.success(node.next)
    }
}
