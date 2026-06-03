package com.ailun.habitat.handlers

import android.util.Base64
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_BASE64]：Base64 编码或解码。
 *
 * params：
 * - `action`（必填）："encode" 或 "decode"
 * - `data`（必填）：要编码/解码的数据
 * - `output_var`（可选）：存储结果的变量名
 */
class NodeBase64Handler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return node.nextResult()

        val action = params["action"]?.toString()?.trim()?.lowercase() ?: run {
            Log.w(TAG, "No action specified")
            context.variables["base64_success"] = false
            return node.nextResult()
        }

        val rawData = params["data"]?.toString()?.trim() ?: run {
            Log.w(TAG, "No data provided")
            context.variables["base64_success"] = false
            return node.nextResult()
        }

        val data = context.interpolate(rawData)
        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null }

        try {
            val result: String = when (action) {
                "encode" -> {
                    Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                }
                "decode" -> {
                    String(Base64.decode(data, Base64.NO_WRAP), Charsets.UTF_8)
                }
                else -> {
                    Log.w(TAG, "Unknown action: $action, expected 'encode' or 'decode'")
                    context.variables["base64_success"] = false
                    return node.nextResult()
                }
            }

            val resultKey = outputVar ?: "base64_result"
            context.variables["base64_result"] = result
            context.variables[resultKey] = result
            context.variables["base64_success"] = true

            Log.i(TAG, "Base64 $action successful: ${result.length} chars")
        } catch (e: Exception) {
            Log.e(TAG, "Base64 $action failed: ${e.message}", e)
            context.variables["base64_success"] = false
            context.variables["base64_error"] = e.message ?: "Unknown error"
        }

        return node.nextResult()
    }

    companion object {
        private const val TAG = "HabitatBase64"
    }
}
