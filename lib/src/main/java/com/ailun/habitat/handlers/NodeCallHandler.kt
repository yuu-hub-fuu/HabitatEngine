package com.ailun.habitat.handlers

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_CALL_PHONE]：打开拨号界面（不直接拨出，无需 CALL_PHONE 权限）。
 */
class NodeCallHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawNumber = node.params?.get("phone_number")?.toString()?.trim().orEmpty()
        if (rawNumber.isEmpty()) {
            Log.e(TAG, "Call failed: 'phone_number' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'phone_number' parameter",
                mapOf("call_success" to false))
        }

        val phoneNumber = context.interpolate(rawNumber)

        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.appContext.startActivity(intent)
            Log.i(TAG, "Successfully opened dialer for: $phoneNumber")
            return NodeResult.success(node.next, mapOf("call_success" to true))
        } catch (e: Exception) {
            Log.e(TAG, "Call error for '$phoneNumber': ${e.message}", e)
            return NodeResult.failure(node.next, "Call failed: ${e.message}",
                mapOf("call_success" to false))
        }
    }

    companion object { private const val TAG = "HabitatCall" }
}
