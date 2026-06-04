package com.ailun.habitat.handlers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_CLIPBOARD]：读取或写入系统剪贴板。
 */
class NodeClipboardHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val action = params["action"]?.toString()?.trim()?.lowercase()
            ?: return NodeResult.failure(node.next, "Missing 'action' parameter",
                mapOf("clipboard_success" to false))

        val clipboardManager = context.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return NodeResult.failure(node.next, "ClipboardManager not available",
                mapOf("clipboard_success" to false))

        try {
            return when (action) {
                "get" -> {
                    val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null }
                        ?: "clipboard_content"
                    val clip = clipboardManager.primaryClip
                    val text = if (clip != null && clip.itemCount > 0) {
                        clip.getItemAt(0)?.text?.toString() ?: ""
                    } else ""
                    Log.i(TAG, "Clipboard read: ${text.take(100)}${if (text.length > 100) "..." else ""}")
                    NodeResult.success(node.next, mapOf(
                        outputVar to text, "clipboard_content" to text, "clipboard_success" to true,
                    ))
                }
                "set" -> {
                    val text = context.interpolate(params["text"]?.toString() ?: "")
                    val clip = ClipData.newPlainText("habitat_clipboard", text)
                    clipboardManager.setPrimaryClip(clip)
                    Log.i(TAG, "Clipboard set: ${text.take(100)}${if (text.length > 100) "..." else ""}")
                    NodeResult.success(node.next, mapOf("clipboard_success" to true))
                }
                else -> NodeResult.failure(node.next, "Unknown action: $action",
                    mapOf("clipboard_success" to false))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard operation failed: ${e.message}", e)
            return NodeResult.failure(node.next, "Clipboard error: ${e.message}",
                mapOf("clipboard_success" to false, "clipboard_error" to (e.message ?: "Unknown")))
        }
    }

    companion object { private const val TAG = "HabitatClipboard" }
}
