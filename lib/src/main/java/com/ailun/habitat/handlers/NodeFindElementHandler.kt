package com.ailun.habitat.handlers

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import kotlinx.coroutines.delay

/**
 * [ACTION_FIND_ELEMENT] : 在当前界面中通过无障碍服务查找符合条件的 UI 元素。
 *
 * params:
 * - `selector` (必需): 用于匹配元素的文本/ID/类名/内容描述
 * - `search_mode` (可选, 默认 "text"): 搜索模式
 *   - "text": 按节点文本 (text) 匹配
 *   - "id": 按 viewId 匹配
 *   - "class": 按类名 (className) 匹配
 *   - "content_desc": 按内容描述 (contentDescription) 匹配
 * - `output_var` (可选): 变量名，用于存储元素详细信息 map
 * - `timeout_ms` (可选, 默认 3000): 查找超时时间（毫秒），期间会轮询重试
 *
 * 输出变量:
 * - `element_found` (Boolean): 是否找到匹配元素
 * - `element_bounds` (String): 元素边界 "left,top,right,bottom"
 * - `element_text` (String): 元素文本内容
 * - `element_clickable` (Boolean): 元素是否可点击
 * - 若指定了 output_var，还会在该变量名下存储包含 found/bounds/text/clickable/contentDescription/className/packageName 的 map
 */
class NodeFindElementHandler(
    private val provider: IAccessibilityProvider?,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawSelector = node.params?.get("selector")?.toString()?.trim().orEmpty()
        if (rawSelector.isEmpty()) {
            Log.w(TAG, "FindElement: 'selector' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'selector' parameter",
                mapOf("element_found" to false))
        }

        val selector = context.interpolate(rawSelector)
        val searchMode = node.params?.get("search_mode")?.toString()?.trim()?.lowercase()
            ?: SEARCH_MODE_TEXT
        val outputVar = node.params?.get("output_var")?.toString()?.trim()?.ifEmpty { null }
        val timeoutMs = (node.params?.get("timeout_ms") as? Number)?.toLong() ?: DEFAULT_TIMEOUT_MS

        val service = provider?.getService() ?: return NodeResult.failure(
            node.next, "Accessibility service not available",
            mapOf("element_found" to false)
        )

        Log.d(TAG, "FindElement: selector='$selector', mode=$searchMode, timeout=${timeoutMs}ms")

        val foundNode = findElementWithTimeout(service, selector, searchMode, timeoutMs)

        if (foundNode != null) {
            try {
                val rect = Rect()
                foundNode.getBoundsInScreen(rect)
                val bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
                val text = foundNode.text?.toString() ?: ""
                val clickable = foundNode.isClickable
                val contentDesc = foundNode.contentDescription?.toString() ?: ""
                val className = foundNode.className?.toString() ?: ""
                val packageName = foundNode.packageName?.toString() ?: ""

                val outVars = mutableMapOf<String, Any?>(
                    "element_found" to true,
                    "element_bounds" to bounds,
                    "element_text" to text,
                    "element_clickable" to clickable,
                )
                if (outputVar != null) {
                    outVars[outputVar] = mapOf(
                        "found" to true, "bounds" to bounds, "text" to text,
                        "clickable" to clickable, "contentDescription" to contentDesc,
                        "className" to className, "packageName" to packageName,
                    )
                }
                context.log("FindElement: element found, bounds=$bounds, clickable=$clickable")
                return NodeResult.success(node.next, outVars)
            } finally {
                foundNode.recycle()
            }
        } else {
            val outVars = mutableMapOf<String, Any?>(
                "element_found" to false,
                "element_bounds" to "",
                "element_text" to "",
                "element_clickable" to false,
            )
            if (outputVar != null) outVars[outputVar] = mapOf("found" to false)
            context.log("FindElement: no element found for selector '$selector'")
            return NodeResult.success(node.next, outVars)
        }
    }

    private suspend fun findElementWithTimeout(
        service: AccessibilityService, selector: String, searchMode: String, timeoutMs: Long,
    ): AccessibilityNodeInfo? {
        val pollInterval = 100L
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = searchElement(service, selector, searchMode)
            if (result != null) return result
            delay(pollInterval)
        }
        return searchElement(service, selector, searchMode)
    }

    private fun searchElement(
        service: AccessibilityService, selector: String, searchMode: String,
    ): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            service.rootInActiveWindow?.let { roots.add(it) }
            service.windows.forEach { win -> win.root?.let { roots.add(it) } }
            for (root in roots) {
                val found = findNodeByMode(root, selector, searchMode)
                if (found != null) return found
            }
            return null
        } finally {
            roots.forEach { root -> try { root.recycle() } catch (_: Exception) {} }
        }
    }

    private fun findNodeByMode(
        root: AccessibilityNodeInfo, selector: String, searchMode: String,
    ): AccessibilityNodeInfo? {
        val primaryResult = searchBySingleMode(root, selector, searchMode)
        if (primaryResult != null) return primaryResult
        val fallbackModes = when (searchMode) {
            SEARCH_MODE_ID -> listOf(SEARCH_MODE_TEXT, SEARCH_MODE_CONTENT_DESC)
            SEARCH_MODE_CLASS -> listOf(SEARCH_MODE_TEXT, SEARCH_MODE_ID)
            SEARCH_MODE_CONTENT_DESC -> listOf(SEARCH_MODE_TEXT, SEARCH_MODE_ID)
            else -> listOf(SEARCH_MODE_ID, SEARCH_MODE_CONTENT_DESC)
        }
        for (fallbackMode in fallbackModes) {
            val result = searchBySingleMode(root, selector, fallbackMode)
            if (result != null) return result
        }
        return null
    }

    private fun searchBySingleMode(
        root: AccessibilityNodeInfo, selector: String, mode: String,
    ): AccessibilityNodeInfo? = when (mode) {
        SEARCH_MODE_ID -> pickBestNode(root.findAccessibilityNodeInfosByViewId(selector))
        SEARCH_MODE_TEXT -> pickBestNode(root.findAccessibilityNodeInfosByText(selector))
        SEARCH_MODE_CLASS -> {
            val results = mutableListOf<AccessibilityNodeInfo>()
            collectNodesByClassName(root, selector.lowercase(), results)
            if (results.isNotEmpty()) pickBestNode(results) else null
        }
        SEARCH_MODE_CONTENT_DESC -> findNodeByContentDescription(root, selector.lowercase())
        else -> { Log.w(TAG, "FindElement: unknown search mode '$mode'"); null }
    }

    private fun pickBestNode(results: List<AccessibilityNodeInfo>?): AccessibilityNodeInfo? {
        if (results == null || results.isEmpty()) return null
        val best = results.find { it.isVisibleToUser } ?: results.first()
        results.forEach { if (it !== best) it.recycle() }
        return best
    }

    private fun collectNodesByClassName(
        node: AccessibilityNodeInfo, classNameLower: String, results: MutableList<AccessibilityNodeInfo>,
    ) {
        val nodeClass = node.className?.toString()?.lowercase() ?: ""
        if (nodeClass.contains(classNameLower)) {
            results.add(node)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectNodesByClassName(child, classNameLower, results) }
            catch (_: Exception) { try { child.recycle() } catch (_: Exception) {} }
        }
    }

    private fun findNodeByContentDescription(
        node: AccessibilityNodeInfo, descriptionLower: String,
    ): AccessibilityNodeInfo? {
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (contentDesc.contains(descriptionLower) && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = findNodeByContentDescription(child, descriptionLower)
                if (found != null) return found
            } catch (_: Exception) { try { child.recycle() } catch (_: Exception) {} }
        }
        return null
    }

    companion object {
        private const val TAG = "HabitatFindElement"
        const val SEARCH_MODE_TEXT = "text"
        const val SEARCH_MODE_ID = "id"
        const val SEARCH_MODE_CLASS = "class"
        const val SEARCH_MODE_CONTENT_DESC = "content_desc"
        private const val DEFAULT_TIMEOUT_MS = 3000L
    }
}
