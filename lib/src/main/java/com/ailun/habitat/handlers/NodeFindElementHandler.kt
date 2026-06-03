package com.ailun.habitat.handlers

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor
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
    private val shellExecutor: IShellExecutor?,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawSelector = node.params?.get("selector")?.toString()?.trim().orEmpty()
        if (rawSelector.isEmpty()) {
            Log.w(TAG, "FindElement: 'selector' parameter is empty")
            context.variables["element_found"] = false
            return node.nextResult()
        }

        val selector = context.interpolate(rawSelector)
        val searchMode = node.params?.get("search_mode")?.toString()?.trim()?.lowercase()
            ?: SEARCH_MODE_TEXT
        val outputVar = node.params?.get("output_var")?.toString()?.trim()?.ifEmpty { null }
        val timeoutMs = (node.params?.get("timeout_ms") as? Number)?.toLong() ?: DEFAULT_TIMEOUT_MS

        val service = provider?.getService() ?: run {
            Log.e(TAG, "FindElement: Accessibility service not available")
            context.variables["element_found"] = false
            return node.nextResult()
        }

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

                context.variables["element_found"] = true
                context.variables["element_bounds"] = bounds
                context.variables["element_text"] = text
                context.variables["element_clickable"] = clickable

                if (outputVar != null) {
                    context.putVariable(
                        outputVar,
                        mapOf(
                            "found" to true,
                            "bounds" to bounds,
                            "text" to text,
                            "clickable" to clickable,
                            "contentDescription" to contentDesc,
                            "className" to className,
                            "packageName" to packageName,
                        ),
                    )
                }

                context.log("FindElement: element found, bounds=$bounds, clickable=$clickable")
            } finally {
                foundNode.recycle()
            }
        } else {
            context.variables["element_found"] = false
            context.variables["element_bounds"] = ""
            context.variables["element_text"] = ""
            context.variables["element_clickable"] = false

            if (outputVar != null) {
                context.putVariable(outputVar, mapOf("found" to false))
            }

            context.log("FindElement: no element found for selector '$selector'")
        }

        return node.nextResult()
    }

    /**
     * 在指定的超时时间内轮询查找元素。
     * 每隔 100 毫秒搜索一次，直到超时或找到元素。
     * 超时后还会执行最后一次搜索。
     */
    private suspend fun findElementWithTimeout(
        service: AccessibilityService,
        selector: String,
        searchMode: String,
        timeoutMs: Long,
    ): AccessibilityNodeInfo? {
        val pollInterval = 100L
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val result = searchElement(service, selector, searchMode)
            if (result != null) return result
            delay(pollInterval)
        }

        // 超时后最后一次尝试
        return searchElement(service, selector, searchMode)
    }

    /**
     * 在当前界面的无障碍根节点中搜索匹配元素。
     * 遍历所有应用窗口的根节点，对每个根节点执行搜索。
     * 无论是否找到结果，都会在 finally 块中回收根节点。
     */
    private fun searchElement(
        service: AccessibilityService,
        selector: String,
        searchMode: String,
    ): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()

        try {
            service.rootInActiveWindow?.let { roots.add(it) }
            service.windows.forEach { win ->
                win.root?.let { roots.add(it) }
            }

            for (root in roots) {
                val found = findNodeByMode(root, selector, searchMode)
                if (found != null) return found
            }

            return null
        } finally {
            roots.forEach { root ->
                try {
                    root.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 按指定搜索模式在单个根节点中查找元素。
     * 如果主搜索模式没有找到结果，会依次回退到其他搜索模式。
     * 对 SDK 返回的结果列表会进行去重回收（保留目标节点，回收其余节点）。
     */
    private fun findNodeByMode(
        root: AccessibilityNodeInfo,
        selector: String,
        searchMode: String,
    ): AccessibilityNodeInfo? {
        // 主搜索
        val primaryResult = searchBySingleMode(root, selector, searchMode)
        if (primaryResult != null) return primaryResult

        // 回退搜索：如果主模式未找到，尝试其他模式
        val fallbackModes = when (searchMode) {
            SEARCH_MODE_ID -> listOf(SEARCH_MODE_TEXT, SEARCH_MODE_CONTENT_DESC)
            SEARCH_MODE_CLASS -> listOf(SEARCH_MODE_TEXT, SEARCH_MODE_ID)
            SEARCH_MODE_CONTENT_DESC -> listOf(SEARCH_MODE_TEXT, SEARCH_MODE_ID)
            else -> listOf(SEARCH_MODE_ID, SEARCH_MODE_CONTENT_DESC) // "text" default
        }

        for (fallbackMode in fallbackModes) {
            val result = searchBySingleMode(root, selector, fallbackMode)
            if (result != null) return result
        }

        return null
    }

    /**
     * 按单一搜索模式在根节点中查找元素。
     * - text/id 模式使用 SDK 的 findAccessibilityNodeInfosByText/ViewId API，返回列表
     * - class/content_desc 模式通过递归遍历节点树进行匹配
     *
     * 返回的 AccessibilityNodeInfo 需要由调用方负责回收。
     */
    private fun searchBySingleMode(
        root: AccessibilityNodeInfo,
        selector: String,
        mode: String,
    ): AccessibilityNodeInfo? {
        when (mode) {
            SEARCH_MODE_ID -> {
                val results = root.findAccessibilityNodeInfosByViewId(selector)
                return pickBestNode(results)
            }
            SEARCH_MODE_TEXT -> {
                val results = root.findAccessibilityNodeInfosByText(selector)
                return pickBestNode(results)
            }
            SEARCH_MODE_CLASS -> {
                val results = mutableListOf<AccessibilityNodeInfo>()
                collectNodesByClassName(root, selector.lowercase(), results)
                return if (results.isNotEmpty()) {
                    pickBestNode(results)
                } else null
            }
            SEARCH_MODE_CONTENT_DESC -> {
                return findNodeByContentDescription(root, selector.lowercase())
            }
            else -> {
                Log.w(TAG, "FindElement: unknown search mode '$mode'")
                return null
            }
        }
    }

    /**
     * 从 SDK 返回的节点列表中选出最佳节点（优先可见），并回收其余节点。
     * 如果列表为 null 或空，返回 null。
     */
    private fun pickBestNode(results: List<AccessibilityNodeInfo>?): AccessibilityNodeInfo? {
        if (results == null || results.isEmpty()) return null
        val best = results.find { it.isVisibleToUser } ?: results.first()
        results.forEach { if (it !== best) it.recycle() }
        return best
    }

    /**
     * 递归遍历节点树，收集 className 包含指定字符串的节点。
     * 一旦匹配成功，不再向下递归到已匹配节点的子节点。
     */
    private fun collectNodesByClassName(
        node: AccessibilityNodeInfo,
        classNameLower: String,
        results: MutableList<AccessibilityNodeInfo>,
    ) {
        val nodeClass = node.className?.toString()?.lowercase() ?: ""
        if (nodeClass.contains(classNameLower)) {
            results.add(node)
            // 不递归进入匹配节点——返回匹配节点本身供调用方操作
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectNodesByClassName(child, classNameLower, results)
            } catch (_: Exception) {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * 递归遍历节点树，查找 contentDescription 包含指定字符串的可见节点。
     * 匹配时返回该节点（调用方负责回收），遍历过程中自动回收非匹配的子节点。
     */
    private fun findNodeByContentDescription(
        node: AccessibilityNodeInfo,
        descriptionLower: String,
    ): AccessibilityNodeInfo? {
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (contentDesc.contains(descriptionLower) && node.isVisibleToUser) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val found = findNodeByContentDescription(child, descriptionLower)
                if (found != null) return found
            } catch (_: Exception) {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
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
