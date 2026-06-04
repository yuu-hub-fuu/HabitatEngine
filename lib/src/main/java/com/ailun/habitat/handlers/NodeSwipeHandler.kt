package com.ailun.habitat.handlers

import com.ailun.habitat.HabitatAccessibility
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider

/**
 * [ACTION_SWIPE]：直线滑动手势（无障碍）。
 * params：`x1`,`y1`,`x2`,`y2`（整数），`duration` 毫秒可选，默认 400。
 */
class NodeSwipeHandler(
    private val a11yProvider: IAccessibilityProvider? = null
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val service = a11yProvider?.getService()
            ?: return NodeResult.failure(node.next, "Accessibility service not available",
                mapOf("swipe_success" to false))
        val p = node.params
            ?: return NodeResult.failure(node.next, "Missing params",
                mapOf("swipe_success" to false))
        val x1 = (p["x1"] as? Number)?.toInt()
            ?: return NodeResult.failure(node.next, "Missing x1",
                mapOf("swipe_success" to false))
        val y1 = (p["y1"] as? Number)?.toInt()
            ?: return NodeResult.failure(node.next, "Missing y1",
                mapOf("swipe_success" to false))
        val x2 = (p["x2"] as? Number)?.toInt()
            ?: return NodeResult.failure(node.next, "Missing x2",
                mapOf("swipe_success" to false))
        val y2 = (p["y2"] as? Number)?.toInt()
            ?: return NodeResult.failure(node.next, "Missing y2",
                mapOf("swipe_success" to false))
        val duration = (p["duration"] as? Number)?.toLong() ?: 400L

        val ok = HabitatAccessibility.dispatchSwipe(service, x1, y1, x2, y2, duration)
        context.log("Swipe ($x1,$y1)→($x2,$y2) dur=${duration}ms ok=$ok")
        return if (ok) {
            NodeResult.success(node.next, mapOf("swipe_success" to true))
        } else {
            NodeResult.failure(node.next, "Swipe gesture dispatch returned false",
                mapOf("swipe_success" to false))
        }
    }
}
