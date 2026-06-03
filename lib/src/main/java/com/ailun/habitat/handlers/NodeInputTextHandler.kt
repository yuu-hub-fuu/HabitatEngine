package com.ailun.habitat.handlers

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_INPUT_TEXT] : 向输入框输入文本。
 *
 * params:
 * - `text` (必需): 文本内容，支持 `${var}` 变量插值
 * - `target` / `selector` (可选): 要查找的输入框描述/文本/ID。
 *   如果提供，先查找匹配的输入框，再对其输入。
 *   如果不提供，使用当前焦点输入框。
 * - `mode` (可选, 默认 "a11y"): "a11y" | "shell" | "clipboard"
 *   - "a11y": ACTION_SET_TEXT（无障碍服务）
 *   - "shell": `input text` + 转义 + 长度限制（备选）
 *   - "clipboard": 粘贴板写入 + KEYCODE_PASTE（兼容不稳定字符）
 *
 * 输出变量:
 * - `input_success` (Boolean)
 * - `input_error` (String, 失败原因)
 */
class NodeInputTextHandler(
    private val provider: IAccessibilityProvider?,
    private val shellExecutor: IShellExecutor?,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val rawText = node.params?.get("text")?.toString().orEmpty()
        if (rawText.isEmpty()) {
            fail(context, "'text' parameter is empty")
            return node.next
        }

        val text = try { context.interpolate(rawText) }
            catch (_: WorkflowContext.MissingVariableException) { rawText }

        val mode = node.params?.get("mode")?.toString()?.trim()?.lowercase() ?: MODE_A11Y
        val target = node.params?.get("target")?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: node.params?.get("selector")?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }

        Log.d(TAG, "InputText: mode=$mode, textLen=${text.length}, target=$target")

        val success = when (mode) {
            MODE_CLIPBOARD -> inputViaClipboard(context, text)
            MODE_SHELL, MODE_IME -> inputViaShell(text, context)
            MODE_A11Y -> inputViaAccessibility(text, target)
            else -> {
                context.log("InputText: unknown mode '$mode', using a11y")
                inputViaAccessibility(text, target)
            }
        }

        context.variables["input_success"] = success
        if (success) {
            context.log("InputText: success (mode=$mode, ${text.length} chars)")
        } else {
            fail(context, "Input failed via $mode")
        }
        return node.next
    }

    // ── A11y mode ──

    private fun inputViaAccessibility(text: String, target: String?): Boolean {
        val service = provider?.getService() ?: run {
            Log.e(TAG, "InputText: Accessibility service not available")
            return false
        }

        val focusedNode: AccessibilityNodeInfo? = if (target != null) {
            findInputNode(service, target)
        } else {
            try {
                service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } catch (e: Exception) {
                Log.e(TAG, "InputText: rootInActiveWindow failed", e)
                null
            }
        }

        if (focusedNode == null) {
            Log.w(TAG, "InputText: no input field found (target=${target ?: "focused"})")
            return false
        }

        return try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text.take(8192),
                )
            }
            val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "InputText: ACTION_SET_TEXT result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "InputText: ACTION_SET_TEXT failed", e)
            false
        } finally {
            focusedNode.recycle()
        }
    }

    /** Find an editable input widget matching selector text or viewId. */
    private fun findInputNode(
        service: android.accessibilityservice.AccessibilityService,
        selector: String,
    ): AccessibilityNodeInfo? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        try {
            service.rootInActiveWindow?.let { roots.add(it) }
            for (root in roots) {
                root.findAccessibilityNodeInfosByText(selector)?.let { candidates.addAll(it) }
                root.findAccessibilityNodeInfosByViewId(selector)?.let { candidates.addAll(it) }
            }
            return candidates.firstOrNull { it.isEditable || it.isFocused }
        } finally {
            // Recycle non-matching nodes immediately
            candidates.forEach { if (!it.isEditable && !it.isFocused) try { it.recycle() } catch (_: Exception) {} }
            roots.forEach { try { it.recycle() } catch (_: Exception) {} }
        }
    }

    // ── Shell mode ──

    private suspend fun inputViaShell(text: String, context: WorkflowContext): Boolean {
        val executor = shellExecutor ?: run {
            context.log("InputText: shell mode requires IShellExecutor")
            return false
        }
        // For text up to 200 ASCII chars, use escaped shell input.
        // Longer text or text with special chars → use clipboard fallback.
        if (text.length > 200 || text.any { it == '\n' || it == '%' || it == '\\' || it.code > 127 }) {
            context.log("InputText: text contains special chars, using clipboard fallback")
            return inputViaClipboard(context, text)
        }
        // Escape single quotes for shell safety
        val escaped = text.replace("'", "'\\''")
        return try {
            val output = executor.exec("input text '$escaped'")
            !output.contains("Error", ignoreCase = true) &&
                !output.contains("exception", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "InputText: shell execution failed", e)
            false
        }
    }

    // ── Clipboard fallback ──

    private fun inputViaClipboard(context: WorkflowContext, text: String): Boolean {
        val service = provider?.getService() ?: return false
        return try {
            // Write to system clipboard
            val clip = android.content.ClipData.newPlainText("habitat_input", text)
            val clipManager = context.appContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
            clipManager?.setPrimaryClip(clip) ?: return false

            // Find focused editable node and perform ACTION_PASTE
            val focusedNode: AccessibilityNodeInfo? = try {
                service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } catch (e: Exception) { null }

            val ok = focusedNode?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true
            focusedNode?.recycle()
            Log.d(TAG, "Clipboard paste via ACTION_PASTE: $ok")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard input failed", e)
            false
        }
    }

    private fun fail(context: WorkflowContext, error: String) {
        context.variables["input_success"] = false
        context.variables["input_error"] = error
        context.variables["_last_error"] = true
        context.variables["_last_error_msg"] = "ACTION_INPUT_TEXT: $error"
    }

    companion object {
        private const val TAG = "HabitatInputText"
        const val MODE_A11Y = "a11y"
        const val MODE_SHELL = "shell"
        const val MODE_IME = "ime"
        const val MODE_CLIPBOARD = "clipboard"
    }
}
