package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_GLOBAL_KEY] : 通过 `input keyevent` 发送全局按键事件。
 */
class NodeGlobalKeyHandler(
    private val shellExecutor: IShellExecutor?,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val key = node.params?.get("key")?.toString()?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) {
            Log.w(TAG, "GlobalKey: 'key' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'key' parameter",
                mapOf("key_success" to false))
        }

        val keyCode = KEY_MAP[key]
        if (keyCode == null) {
            Log.w(TAG, "GlobalKey: unknown key '$key', valid keys: ${KEY_MAP.keys}")
            return NodeResult.failure(node.next, "Unknown key: $key",
                mapOf("key_success" to false))
        }

        val executor = shellExecutor ?: return NodeResult.failure(
            node.next, "IShellExecutor not available",
            mapOf("key_success" to false)
        )

        Log.d(TAG, "GlobalKey: sending keyevent $keyCode for key '$key'")

        val success = try {
            val output = executor.exec("input keyevent $keyCode")
            !output.contains("Error", ignoreCase = true) &&
                !output.contains("exception", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "GlobalKey: shell execution failed for key '$key'", e)
            false
        }

        if (success) {
            context.log("GlobalKey: key '$key' (code=$keyCode) executed successfully")
            return NodeResult.success(node.next, mapOf("key_success" to true))
        } else {
            context.log("GlobalKey: key '$key' (code=$keyCode) execution failed")
            return NodeResult.failure(node.next, "Key execution failed",
                mapOf("key_success" to false))
        }
    }

    companion object {
        private const val TAG = "HabitatGlobalKey"
        val KEY_MAP: Map<String, Int> = mapOf(
            "back" to 4, "home" to 3, "recents" to 187,
            "notifications" to 83, "quick_settings" to 84,
            "power_dialog" to 26, "screenshot_toggle" to 120,
            "enter" to 66, "delete" to 67, "space" to 62, "tab" to 61,
        )
    }
}
