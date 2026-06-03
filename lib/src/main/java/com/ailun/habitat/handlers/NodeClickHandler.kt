package com.ailun.habitat.handlers

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ailun.habitat.HabitatAccessibility
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider

/**
 * [ACTION_CLICK]：点击指定目标。
 *
 * params:
 * - `target`：要点击的文本或 viewId（文本匹配模式）
 * - `x` / `y`：屏幕坐标系坐标（坐标模式，精确点击）
 * - `allow_self_package`：是否允许点击本应用自身的 UI 元素（默认 false）
 *
 * 两种模式互斥：先检查 x/y 坐标，再退回到 target 文本查找。
 */
class NodeClickHandler(
    private val a11yProvider: IAccessibilityProvider? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: emptyMap()
        val service = a11yProvider?.getService()

        if (service == null) {
            context.variables["click_success"] = false
            context.variables["click_error"] = "Accessibility Service not running"
            context.variables["_last_error"] = true
            context.variables["_last_error_msg"] = "ACTION_CLICK: Accessibility Service not running"
            Log.e(TAG, "Click failed: Accessibility Service not running")
            return node.nextResult()
        }

        // ── Coordinate mode: explicit x, y params ──
        val xParam = params["x"]
        val yParam = params["y"]
        if (xParam != null && yParam != null) {
            val x = (xParam as? Number)?.toInt() ?: xParam.toString().toIntOrNull() ?: run {
                fail(context, "Invalid x coordinate: $xParam"); return node.nextResult()
            }
            val y = (yParam as? Number)?.toInt() ?: yParam.toString().toIntOrNull() ?: run {
                fail(context, "Invalid y coordinate: $yParam"); return node.nextResult()
            }
            val ok = HabitatAccessibility.dispatchTap(service, x, y)
            context.variables["click_success"] = ok
            if (!ok) fail(context, "Coordinate tap failed at ($x, $y)")
            Log.i(TAG, "Coordinate click ($x, $y): $ok")
            return node.nextResult()
        }

        // ── Target mode: text or viewId ──
        val raw = params["target"]?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) {
            fail(context, "'target' parameter is empty (provide 'target' or 'x'/'y')")
            return node.nextResult()
        }

        val target = try {
            context.interpolate(raw)
        } catch (_: WorkflowContext.MissingVariableException) {
            raw
        }

        val allowSelf = params["allow_self_package"]?.toString()?.equals("true", true) == true
        val selfPackage = context.appContext.packageName
        Log.d(TAG, "Click target: '$target' allowSelf=$allowSelf")

        val success = performA11yClick(service, target, selfPackage, allowSelf)
        context.variables["click_success"] = success
        if (!success) fail(context, "No clickable node found for '$target'")
        Log.i(TAG, "Click result for '$target': $success")
        return node.nextResult()
    }

    private fun performA11yClick(
        service: android.accessibilityservice.AccessibilityService,
        selector: String,
        selfPackage: String,
        allowSelf: Boolean,
    ): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        // Track nodes we've obtained (not roots) so we only recycle those
        val obtainedNodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            // Get root nodes — these we must recycle
            service.rootInActiveWindow?.let { roots.add(it) }
            service.windows.forEach { win -> win.root?.let { roots.add(it) } }

            for (root in roots) {
                root.findAccessibilityNodeInfosByViewId(selector)?.let { obtainedNodes.addAll(it) }
                root.findAccessibilityNodeInfosByText(selector)?.let { obtainedNodes.addAll(it) }
            }

            val candidates = if (allowSelf) {
                obtainedNodes
            } else {
                obtainedNodes.filter { node ->
                    val pkg = node.packageName?.toString()
                    pkg != null && pkg != selfPackage
                }
            }

            if (candidates.isEmpty()) {
                Log.w(TAG, "No node found for: '$selector'")
                return false
            }

            val targetNode = candidates.find { it.isVisibleToUser } ?: candidates.first()
            val r = Rect()
            targetNode.getBoundsInScreen(r)
            Log.d(TAG, "Click target: text=${targetNode.text}, pkg=${targetNode.packageName}, bounds=$r")

            var ok = HabitatAccessibility.dispatchTap(service, r.centerX(), r.centerY())
            if (!ok) {
                // Fall back: try ACTION_CLICK on the node and its ancestors
                ok = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!ok && !targetNode.isClickable) {
                    var parent: AccessibilityNodeInfo? = targetNode.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            ok = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            break
                        }
                        parent = parent.parent
                    }
                    // Recycle the parent chain — we don't own these references
                    parent?.recycle()
                }
            }
            return ok
        } finally {
            obtainedNodes.forEach { try { it.recycle() } catch (_: Exception) {} }
            // Recycle roots only if we own them (AccessibilityService returns new nodes)
            roots.forEach { try { it.recycle() } catch (_: Exception) {} }
        }
    }

    private fun fail(context: WorkflowContext, error: String) {
        context.variables["click_success"] = false
        context.variables["click_error"] = error
        context.variables["_last_error"] = true
        context.variables["_last_error_msg"] = error
    }

    companion object {
        private const val TAG = "HabitatClick"
    }
}
