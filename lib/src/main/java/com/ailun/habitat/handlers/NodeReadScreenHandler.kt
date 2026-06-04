package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ailun.habitat.api.IAccessibilityProvider

/**
 * [ACTION_READ_SCREEN]：在当前界面查找关键字或抓取全屏文本。
 */
class NodeReadScreenHandler(
    private val a11yProvider: IAccessibilityProvider? = null
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawKeyword = node.params?.get("keyword")?.toString().orEmpty()
        val keyword = context.interpolate(rawKeyword)
        val outputVar = node.params?.get("output_var")?.toString().orEmpty()

        val service = a11yProvider?.getService()
            ?: run {
                context.log("ReadScreen Error: Accessibility not running")
                context.variables["screen_data"] = false
                return NodeResult.success(node.next)
            }

        val roots = mutableListOf<AccessibilityNodeInfo>()
        service.windows.forEach { window ->
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                window.root?.let { r ->
                    val pkg = r.packageName?.toString()
                    if (pkg != null) {
                        roots.add(r)
                    } else {
                        r.recycle()
                    }
                }
            }
        }

        if (roots.isEmpty()) {
            service.rootInActiveWindow?.let { r ->
                val pkg = r.packageName?.toString()
                if (pkg != null) {
                    roots.add(r)
                } else {
                    r.recycle()
                }
            }
        }

        if (roots.isEmpty()) {
            context.variables["screen_data"] = false
            if (outputVar.isNotEmpty()) context.putVariable(outputVar, "")
            return NodeResult.success(node.next)
        }

        var found = false
        val allTexts = mutableListOf<String>()
        try {
            for (root in roots) {
                if (outputVar.isNotEmpty()) collectAllTextRecursive(root, allTexts)
                if (containsKeywordRecursive(root, keyword)) {
                    found = true
                    break
                }
            }
            context.variables["screen_data"] = found
            if (found) context.log("ReadScreen: Target found in external app.")

            if (outputVar.isNotEmpty()) {
                context.putVariable(outputVar, allTexts.joinToString("\n"))
            }
        } finally {
            roots.forEach { it.recycle() }
        }
        return NodeResult.success(node.next)
    }

    private fun containsKeywordRecursive(node: AccessibilityNodeInfo, keyword: String): Boolean {
        if (keyword.isEmpty()) return false
        if (!node.isVisibleToUser) return false
        val pkg = node.packageName?.toString() ?: ""
        if (pkg.isEmpty()) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (text.contains(keyword, ignoreCase = true) || desc.contains(keyword, ignoreCase = true)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { if (containsKeywordRecursive(child, keyword)) return true }
            finally { child.recycle() }
        }
        return false
    }

    private fun collectAllTextRecursive(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        if (!node.isVisibleToUser) return
        val pkg = node.packageName?.toString() ?: ""
        if (pkg.isEmpty()) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) texts.add(text)
        if (!desc.isNullOrEmpty() && desc != text) texts.add(desc)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectAllTextRecursive(child, texts) }
            finally { child.recycle() }
        }
    }
}
