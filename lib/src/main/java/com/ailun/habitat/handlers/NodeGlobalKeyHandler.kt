package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_GLOBAL_KEY] : 通过 `input keyevent` 发送全局按键事件。
 *
 * params:
 * - `key` (必需): 按键标识符，支持以下值:
 *   - back          → KEYCODE_BACK (4)
 *   - home          → KEYCODE_HOME (3)
 *   - recents       → KEYCODE_APP_SWITCH (187)
 *   - notifications → KEYCODE_NOTIFICATION (83)
 *   - quick_settings → KEYCODE_QUICK_SETTINGS (84)
 *   - power_dialog  → KEYCODE_POWER (26)
 *   - screenshot_toggle → KEYCODE_SYSRQ (120)
 *
 * 输出变量:
 * - `key_success` (Boolean): 按键事件是否执行成功
 */
class NodeGlobalKeyHandler(
    private val provider: IAccessibilityProvider?,
    private val shellExecutor: IShellExecutor?,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val key = node.params?.get("key")?.toString()?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) {
            Log.w(TAG, "GlobalKey: 'key' parameter is empty")
            context.variables["key_success"] = false
            return NodeResult.success(node.next)
        }

        val keyCode = KEY_MAP[key]
        if (keyCode == null) {
            Log.w(TAG, "GlobalKey: unknown key '$key', valid keys: ${KEY_MAP.keys}")
            context.variables["key_success"] = false
            return NodeResult.success(node.next)
        }

        val executor = shellExecutor ?: run {
            Log.e(TAG, "GlobalKey: IShellExecutor not available")
            context.variables["key_success"] = false
            return NodeResult.success(node.next)
        }

        Log.d(TAG, "GlobalKey: sending keyevent $keyCode for key '$key'")

        val success = try {
            val output = executor.exec("input keyevent $keyCode")
            !output.contains("Error", ignoreCase = true) &&
                !output.contains("exception", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "GlobalKey: shell execution failed for key '$key'", e)
            false
        }

        context.variables["key_success"] = success
        if (success) {
            context.log("GlobalKey: key '$key' (code=$keyCode) executed successfully")
        } else {
            context.log("GlobalKey: key '$key' (code=$keyCode) execution failed")
        }

        return NodeResult.success(node.next)
    }

    companion object {
        private const val TAG = "HabitatGlobalKey"

        val KEY_MAP: Map<String, Int> = mapOf(
            "back" to 4,
            "home" to 3,
            "recents" to 187,
            "notifications" to 83,
            "quick_settings" to 84,
            "power_dialog" to 26,
            "screenshot_toggle" to 120,
            "enter" to 66,
            "delete" to 67,
            "space" to 62,
            "tab" to 61,
        )
    }
}
