package com.ailun.habitat.handlers

import com.ailun.habitat.HabitatAccessibility
import com.ailun.habitat.INodeHandler
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

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val service = a11yProvider?.getService() ?: return node.next
        val p = node.params ?: return node.next
        val x1 = (p["x1"] as? Number)?.toInt() ?: return node.next
        val y1 = (p["y1"] as? Number)?.toInt() ?: return node.next
        val x2 = (p["x2"] as? Number)?.toInt() ?: return node.next
        val y2 = (p["y2"] as? Number)?.toInt() ?: return node.next
        val duration = (p["duration"] as? Number)?.toLong() ?: 400L

        val ok = HabitatAccessibility.dispatchSwipe(service, x1, y1, x2, y2, duration)
        context.variables["swipe_success"] = ok
        context.log("Swipe ($x1,$y1)→($x2,$y2) dur=${duration}ms ok=$ok")
        return node.next
    }
}
