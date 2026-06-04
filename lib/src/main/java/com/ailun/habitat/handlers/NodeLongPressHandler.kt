package com.ailun.habitat.handlers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import kotlinx.coroutines.CompletableDeferred

/**
 * [ACTION_LONG_PRESS] : 在指定目标上执行长按操作。
 */
class NodeLongPressHandler(
    private val provider: IAccessibilityProvider?,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawTarget = node.params?.get("target")?.toString()?.trim().orEmpty()
        if (rawTarget.isEmpty()) {
            Log.w(TAG, "LongPress: 'target' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'target' parameter",
                mapOf("long_press_success" to false))
        }

        val target = context.interpolate(rawTarget)
        val durationMs = (node.params?.get("duration") as? Number)?.toLong() ?: DEFAULT_DURATION_MS

        val service = provider?.getService() ?: return NodeResult.failure(
            node.next, "Accessibility service not available",
            mapOf("long_press_success" to false)
        )

        Log.d(TAG, "LongPress: target='$target', duration=${durationMs}ms")

        val normalizedTarget = target.trim()
        val success = if (normalizedTarget.contains(",")) {
            val parts = normalizedTarget.split(",")
            val x = parts[0].trim().toIntOrNull()
            val y = parts[1].trim().toIntOrNull()
            if (x != null && y != null) {
                longPressCoordinate(service, x, y, durationMs)
            } else {
                Log.w(TAG, "LongPress: invalid coordinate format '$target'")
                false
            }
        } else {
            longPressAccessibilityNode(service, normalizedTarget, context.appContext.packageName, durationMs)
        }

        if (success) {
            context.log("LongPress: operation successful on '$target'")
            return NodeResult.success(node.next, mapOf("long_press_success" to true))
        } else {
            context.log("LongPress: operation failed on '$target'")
            return NodeResult.failure(node.next, "LongPress failed on '$target'",
                mapOf("long_press_success" to false))
        }
    }

    private suspend fun longPressCoordinate(
        service: AccessibilityService, x: Int, y: Int, durationMs: Long,
    ): Boolean {
        if (x < 0 || y < 0) { Log.w(TAG, "LongPress: negative coordinates ($x, $y)"); return false }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAwait(service, gesture)
    }

    private suspend fun longPressAccessibilityNode(
        service: AccessibilityService, selector: String, selfPackage: String, durationMs: Long,
    ): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            service.rootInActiveWindow?.let { roots.add(it) }
            service.windows.forEach { win -> win.root?.let { roots.add(it) } }
            if (roots.isEmpty()) { Log.w(TAG, "LongPress: no accessibility roots available"); return false }
            for (root in roots) {
                val result = searchAndLongPressInRoot(root, selector, selfPackage, durationMs, service)
                if (result) return true
            }
            Log.w(TAG, "LongPress: no matching node found for selector '$selector'")
            return false
        } finally {
            roots.forEach { root -> try { root.recycle() } catch (_: Exception) {} }
        }
    }

    private suspend fun searchAndLongPressInRoot(
        root: AccessibilityNodeInfo, selector: String, selfPackage: String,
        durationMs: Long, service: AccessibilityService,
    ): Boolean {
        val foundNodes = mutableListOf<AccessibilityNodeInfo>()
        root.findAccessibilityNodeInfosByViewId(selector)?.let { foundNodes.addAll(it) }
        if (foundNodes.isEmpty()) root.findAccessibilityNodeInfosByText(selector)?.let { foundNodes.addAll(it) }
        if (foundNodes.isEmpty()) return false

        val externalNodes = foundNodes.filter { node ->
            val pkg = node.packageName?.toString(); pkg != null && pkg != selfPackage
        }
        if (externalNodes.isEmpty()) { foundNodes.forEach { it.recycle() }; return false }

        val targetNode = externalNodes.find { it.isVisibleToUser } ?: externalNodes.first()
        foundNodes.forEach { if (it !== targetNode) it.recycle() }

        val nodeRect = Rect()
        targetNode.getBoundsInScreen(nodeRect)
        Log.d(TAG, "LongPress: found node text='${targetNode.text}', " +
            "pkg=${targetNode.packageName}, bounds=$nodeRect, " +
            "longClickable=${targetNode.isLongClickable}")

        var result = try { targetNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }
            catch (e: Exception) { Log.e(TAG, "LongPress: ACTION_LONG_CLICK threw", e); false }

        if (!result && !targetNode.isLongClickable) {
            var parent: AccessibilityNodeInfo? = targetNode.parent
            while (parent != null) {
                if (parent.isLongClickable) {
                    result = try { parent.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }
                        catch (e: Exception) { false }
                    parent.recycle()
                    if (result) break
                } else {
                    val gp = parent.parent; parent.recycle(); parent = gp
                }
            }
        }
        targetNode.recycle()

        if (!result) {
            Log.d(TAG, "LongPress: falling back to gesture at (${nodeRect.centerX()}, ${nodeRect.centerY()})")
            result = longPressCoordinate(service, nodeRect.centerX(), nodeRect.centerY(), durationMs)
        }
        return result
    }

    private suspend fun dispatchGestureAwait(
        service: AccessibilityService, gesture: GestureDescription,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        try {
            service.dispatchGesture(gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gesture: GestureDescription?) { deferred.complete(true) }
                    override fun onCancelled(gesture: GestureDescription?) { deferred.complete(false) }
                }, null)
        } catch (e: Exception) { Log.e(TAG, "LongPress: dispatchGesture failed", e); return false }
        return deferred.await()
    }

    companion object {
        private const val TAG = "HabitatLongPress"
        private const val DEFAULT_DURATION_MS = 800L
    }
}
