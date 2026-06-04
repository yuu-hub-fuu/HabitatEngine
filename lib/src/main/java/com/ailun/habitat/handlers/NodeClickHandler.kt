package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider

/**
 * [ACTION_CLICK] — 点击屏幕元素。
 *
 * FIXME: Currently a stub. Real click implementation pending accessibility
 * service integration (GestureDescription or dispatchGesture).
 */
class NodeClickHandler(
    private val a11yProvider: IAccessibilityProvider? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val target = node.params?.get("target")?.toString() ?: ""
        if (target.isEmpty()) {
            return NodeResult.failure(
                next = node.branches?.get("error") ?: node.next,
                error = "Missing 'target' parameter for click",
                vars = mapOf("click_success" to false),
            )
        }
        val service = a11yProvider?.getService()
        if (service == null) {
            return NodeResult.failure(
                next = node.branches?.get("error") ?: node.next,
                error = "Accessibility service not available for click",
                vars = mapOf("click_success" to false),
            )
        }
        // FIXME: implement GestureDescription click on target element
        context.log("CLICK (stub): target=$target")
        return NodeResult.success(node.next, mapOf("click_success" to true))
    }
}
