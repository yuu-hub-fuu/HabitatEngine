package com.ailun.habitat.handlers

import android.content.Intent
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_SHARE]：通过系统分享面板分享内容。
 */
class NodeShareHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val rawText = params["text"]?.toString()?.trim()
            ?: return NodeResult.failure(node.next, "Missing 'text' parameter",
                mapOf("share_success" to false))

        val text = context.interpolate(rawText)
        val title = params["title"]?.toString()?.trim()?.let { context.interpolate(it) } ?: "分享"
        val mimeType = params["type"]?.toString()?.trim() ?: "text/plain"
        val subject = params["subject"]?.toString()?.trim()?.let { context.interpolate(it) }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, text)
                if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.appContext.startActivity(chooserIntent)
            Log.i(TAG, "Share intent launched: type=$mimeType, text=${text.take(50)}...")
            return NodeResult.success(node.next, mapOf("share_success" to true))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed: ${e.message}", e)
            return NodeResult.failure(node.next, "Share failed: ${e.message}",
                mapOf("share_success" to false, "share_error" to (e.message ?: "Unknown")))
        }
    }

    companion object { private const val TAG = "HabitatShare" }
}
