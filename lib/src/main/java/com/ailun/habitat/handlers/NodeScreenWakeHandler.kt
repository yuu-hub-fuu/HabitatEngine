package com.ailun.habitat.handlers

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_SCREEN_WAKE]：控制屏幕唤醒/睡眠/解锁。
 */
class NodeScreenWakeHandler(
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Screen wake failed: 'action' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'action' parameter",
                mapOf("screen_wake_success" to false))
        }

        val password = node.params?.get("password")?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val success = when (action) {
            "wake" -> wakeScreen(context)
            "sleep" -> sleepScreen(context)
            "wake_and_unlock" -> wakeAndUnlock(context, password)
            else -> {
                Log.e(TAG, "Screen wake failed: unknown action '$action'")
                return NodeResult.failure(node.next, "Unknown action: $action",
                    mapOf("screen_wake_success" to false))
            }
        }

        if (success) {
            Log.i(TAG, "Screen action '$action' succeeded")
            return NodeResult.success(node.next, mapOf("screen_wake_success" to true))
        } else {
            Log.e(TAG, "Screen action '$action' failed")
            return NodeResult.failure(node.next, "Screen action '$action' failed",
                mapOf("screen_wake_success" to false))
        }
    }

    private suspend fun wakeScreen(context: WorkflowContext): Boolean {
        if (shellExecutor != null) {
            try {
                var output = shellExecutor.exec("input keyevent 224", asRoot = false)
                if (!output.contains("Error", ignoreCase = true)) return true
                output = shellExecutor.exec("input keyevent 26", asRoot = false)
                if (!output.contains("Error", ignoreCase = true)) return true
            } catch (e: Exception) { Log.w(TAG, "Shell wake keyevent failed: ${e.message}") }
        }
        try {
            val powerManager = context.appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE, "$TAG::wakeLock")
                wakeLock.acquire(1000L)
                wakeLock.release()
                return true
            }
        } catch (e: Exception) { Log.w(TAG, "PowerManager wakeLock failed: ${e.message}") }
        return false
    }

    private suspend fun sleepScreen(context: WorkflowContext): Boolean {
        if (shellExecutor != null) {
            try {
                val output = shellExecutor.exec("input keyevent 26", asRoot = false)
                if (!output.contains("Error", ignoreCase = true)) return true
            } catch (e: Exception) { Log.w(TAG, "Shell sleep keyevent failed: ${e.message}") }
        }
        return false
    }

    private suspend fun wakeAndUnlock(context: WorkflowContext, password: String?): Boolean {
        if (!wakeScreen(context)) return false
        if (shellExecutor == null) return true // screen is awake, can't unlock further
        try {
            shellExecutor.exec("input keyevent 82", asRoot = false)
            if (password != null) {
                kotlinx.coroutines.delay(500)
                shellExecutor.exec("input text \"$password\"", asRoot = false)
                kotlinx.coroutines.delay(200)
                shellExecutor.exec("input keyevent 66", asRoot = false)
            }
            return true
        } catch (e: Exception) { Log.w(TAG, "Shell unlock sequence failed: ${e.message}"); return false }
    }

    companion object { private const val TAG = "HabitatScreenWake" }
}
