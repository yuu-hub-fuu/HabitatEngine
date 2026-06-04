package com.ailun.habitat.handlers

import android.view.accessibility.AccessibilityNodeInfo
import com.ailun.habitat.HabitatAccessibility
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider

/**
 * [ACTION_CLICK] — 点击屏幕元素。
 *
 * params:
 * - `target` (必填): 目标描述，支持:
 *   - 坐标 "x,y": 如 "500,1200" — 在指定坐标执行手势点击
 *   - 文本选择器: 如 "确定" / "com.example:id/btn" — 查找 UI 元素后点击
 * - `find_timeout_ms` (可选): 文本匹配超时，默认 2000ms
 *
 * 输出变量:
 * - `click_success` (Boolean)
 */
class NodeClickHandler(
    private val a11yProvider: IAccessibilityProvider? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawTarget = node.params?.get("target")?.toString()?.trim().orEmpty()
        if (rawTarget.isEmpty()) {
            return NodeResult.failure(node.next, "Missing 'target' parameter for click",
                mapOf("click_success" to false))
        }

        val target = context.interpolate(rawTarget)
        val service = a11yProvider?.getService()
            ?: return NodeResult.failure(node.next, "Accessibility service not available",
                mapOf("click_success" to false))

        val success = if (target.contains(",") && isCoordinate(target)) {
            // Coordinate mode
            val parts = target.split(",")
            val x = parts[0].trim().toIntOrNull()
            val y = parts[1].trim().toIntOrNull()
            if (x != null && y != null && x >= 0 && y >= 0) {
                context.log("CLICK: gesture at ($x, $y)")
                HabitatAccessibility.dispatchTap(service, x, y)
            } else {
                context.log("CLICK: invalid coordinates '$target'")
                false
            }
        } else {
            // Selector mode — find element and click it
            clickElement(service, target, context)
        }

        return if (success) {
            context.log("CLICK: success on '$target'")
            NodeResult.success(node.next, mapOf("click_success" to true))
        } else {
            context.log("CLICK: failed on '$target'")
            NodeResult.failure(node.next, "Click failed on '$target'",
                mapOf("click_success" to false))
        }
    }

    /** Click a UI element found by text or view ID selector. */
    private suspend fun clickElement(
        service: android.accessibilityservice.AccessibilityService,
        selector: String,
        context: WorkflowContext,
    ): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            service.rootInActiveWindow?.let { roots.add(it) }
            service.windows.forEach { win -> win.root?.let { roots.add(it) } }

            for (root in roots) {
                // Try view ID first, then text
                val found = mutableListOf<AccessibilityNodeInfo>()
                root.findAccessibilityNodeInfosByViewId(selector)?.let { found.addAll(it) }
                if (found.isEmpty()) root.findAccessibilityNodeInfosByText(selector)?.let { found.addAll(it) }
                if (found.isNotEmpty()) {
                    // Prefer visible and clickable
                    val node = found.find { it.isVisibleToUser && it.isClickable }
                        ?: found.find { it.isVisibleToUser }
                        ?: found.first()
                    // Recycle others
                    found.forEach { if (it !== node) it.recycle() }

                    val clickable = node.isClickable
                    val result = if (clickable) {
                        try { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                        catch (_: Exception) { false }
                    } else {
                        // Perform click at node's center via gesture
                        val rect = android.graphics.Rect()
                        node.getBoundsInScreen(rect)
                        context.log("CLICK: node not clickable, using gesture at center (${rect.centerX()}, ${rect.centerY()})")
                        HabitatAccessibility.dispatchTap(service, rect.centerX(), rect.centerY())
                    }
                    node.recycle()
                    return result
                }
            }
            return false
        } finally {
            roots.forEach { try { it.recycle() } catch (_: Exception) {} }
        }
    }

    private fun isCoordinate(target: String): Boolean {
        val parts = target.split(",")
        return parts.size == 2 && parts[0].trim().toIntOrNull() != null && parts[1].trim().toIntOrNull() != null
    }
}
