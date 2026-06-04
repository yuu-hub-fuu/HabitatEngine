package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import kotlinx.coroutines.delay

/**
 * [ACTION_WAIT_FOR] — Poll until a condition is met or timeout expires.
 *
 * params:
 * - `condition` (required): One of:
 *     "screen_contains", "element_visible", "variable", "package_foreground"
 * - `target` (required for screen/element): The text/viewId to wait for
 * - `variable` (required for variable mode): Context variable name
 * - `expected_value` (required for variable mode): Expected variable value
 * - `timeout_ms` (optional): Max wait time in ms. Default 10_000.
 * - `poll_interval_ms` (optional): Check interval in ms. Default 500.
 * - `mode` (optional for screen modes): "a11y" (default)
 *
 * Output:
 * - `wait_success` (Boolean): true if condition met within timeout
 * - `wait_elapsed_ms` (Long): actual time waited
 */
class NodeWaitForHandler(
    private val a11yProvider: IAccessibilityProvider? = null,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: emptyMap()
        val condition = params["condition"]?.toString()?.trim()?.lowercase() ?: run {
            return NodeResult.failure(node.next, "Missing 'condition' parameter",
                mapOf("wait_success" to false, "wait_error" to "Missing 'condition' parameter"))
        }
        val target = params["target"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val varName = params["variable"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val expected = params["expected_value"]?.toString()
        val timeoutMs = (params["timeout_ms"] as? Number)?.toLong()?.coerceIn(500, 120_000) ?: 10_000L
        val pollMs = (params["poll_interval_ms"] as? Number)?.toLong()?.coerceIn(100, 5_000) ?: 500L

        context.log("WAIT_FOR: condition='$condition' timeout=${timeoutMs}ms poll=${pollMs}ms")

        val startTime = System.currentTimeMillis()
        val deadline = startTime + timeoutMs
        var met = false

        while (System.currentTimeMillis() < deadline) {
            met = when (condition) {
                "screen_contains", "element_visible" -> {
                    val text = target ?: break
                    checkScreenContains(text)
                }
                "variable" -> {
                    val name = varName ?: break
                    val actual = context.getVariable(name)?.toString() ?: ""
                    expected == null || actual == expected
                }
                "package_foreground" -> {
                    val pkg = target ?: break
                    a11yProvider?.foregroundPackage == pkg
                }
                else -> break
            }
            if (met) break
            delay(pollMs)
        }

        val actualElapsed = System.currentTimeMillis() - startTime
        val elapsed = deadline - System.currentTimeMillis()

        if (met) {
            context.log("WAIT_FOR: condition '$condition' met after ${actualElapsed}ms")
            return NodeResult.success(node.next, mapOf(
                "wait_success" to true,
                "wait_elapsed_ms" to actualElapsed,
            ))
        } else {
            val msg = "WaitFor timed out after ${actualElapsed}ms waiting for '$condition'"
            context.log("WAIT_FOR: $msg")
            return NodeResult.failure(
                next = node.branches?.get("error") ?: node.next,
                error = msg,
                vars = mapOf("wait_success" to false, "wait_elapsed_ms" to actualElapsed),
            )
        }
    }

    private fun checkScreenContains(text: String): Boolean {
        val service = a11yProvider?.getService() ?: return false
        val roots = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        var found = false
        try {
            service.rootInActiveWindow?.let { roots.add(it) }
            for (root in roots) {
                val nodes = root.findAccessibilityNodeInfosByText(text)
                if (nodes.isNotEmpty()) { found = true; break }
            }
        } catch (_: Exception) { } finally {
            roots.forEach { try { it.recycle() } catch (_: Exception) {} }
        }
        return found
    }

}
