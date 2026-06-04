package com.ailun.habitat.handlers

import android.util.Base64
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_BASE64]：Base64 编码或解码。
 */
class NodeBase64Handler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val action = params["action"]?.toString()?.trim()?.lowercase()
            ?: return NodeResult.failure(node.next, "Missing 'action' parameter",
                mapOf("base64_success" to false))

        val rawData = params["data"]?.toString()?.trim()
            ?: return NodeResult.failure(node.next, "Missing 'data' parameter",
                mapOf("base64_success" to false))

        val data = context.interpolate(rawData)
        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null }

        try {
            val result: String = when (action) {
                "encode" -> Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                "decode" -> String(Base64.decode(data, Base64.NO_WRAP), Charsets.UTF_8)
                else -> return NodeResult.failure(node.next, "Unknown action: $action",
                    mapOf("base64_success" to false))
            }

            val resultKey = outputVar ?: "base64_result"
            Log.i(TAG, "Base64 $action successful: ${result.length} chars")
            return NodeResult.success(node.next, mapOf(
                "base64_result" to result, resultKey to result, "base64_success" to true,
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Base64 $action failed: ${e.message}", e)
            return NodeResult.failure(node.next, "Base64 $action failed: ${e.message}",
                mapOf("base64_success" to false, "base64_error" to (e.message ?: "Unknown")))
        }
    }

    companion object { private const val TAG = "HabitatBase64" }
}
