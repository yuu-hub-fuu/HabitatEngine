package com.ailun.habitat.handlers

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_SCREEN_WAKE]：控制屏幕唤醒/睡眠/解锁。
 *
 * params：`action`（必填："wake"/"sleep"/"wake_and_unlock"），`password`（可选，用于解锁）
 */
class NodeScreenWakeHandler(
    private val provider: IAccessibilityProvider? = null,
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Screen wake failed: 'action' parameter is empty")
            context.variables["screen_wake_success"] = false
            return NodeResult.success(node.next)
        }

        val password = node.params?.get("password")?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }

        var success = false

        when (action) {
            "wake" -> {
                success = wakeScreen(context)
            }
            "sleep" -> {
                success = sleepScreen(context)
            }
            "wake_and_unlock" -> {
                success = wakeAndUnlock(context, password)
            }
            else -> {
                Log.e(TAG, "Screen wake failed: unknown action '$action'")
                context.variables["screen_wake_success"] = false
                return NodeResult.success(node.next)
            }
        }

        context.variables["screen_wake_success"] = success
        if (success) {
            Log.i(TAG, "Screen action '$action' succeeded")
        } else {
            Log.e(TAG, "Screen action '$action' failed")
        }

        return NodeResult.success(node.next)
    }

    private suspend fun wakeScreen(context: WorkflowContext): Boolean {
        // Try shell keyevents first
        if (shellExecutor != null) {
            try {
                // Try KEYCODE_WAKEUP (224) first
                var output = shellExecutor.exec("input keyevent 224", asRoot = false)
                if (!output.contains("Error", ignoreCase = true)) {
                    Log.i(TAG, "Screen woken via KEYCODE_WAKEUP (224)")
                    return true
                }
                // Fallback: power key (26)
                output = shellExecutor.exec("input keyevent 26", asRoot = false)
                if (!output.contains("Error", ignoreCase = true)) {
                    Log.i(TAG, "Screen woken via KEYCODE_POWER (26)")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shell wake keyevent failed: ${e.message}")
            }
        }

        // Alternative: PowerManager wakeLock
        try {
            val powerManager = context.appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "$TAG::wakeLock"
                )
                wakeLock.acquire(1000L)
                wakeLock.release()
                Log.i(TAG, "Screen woken via PowerManager wakeLock")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "PowerManager wakeLock failed: ${e.message}")
        }

        return false
    }

    private suspend fun sleepScreen(context: WorkflowContext): Boolean {
        if (shellExecutor != null) {
            try {
                val output = shellExecutor.exec("input keyevent 26", asRoot = false)
                if (!output.contains("Error", ignoreCase = true)) {
                    Log.i(TAG, "Screen put to sleep via KEYCODE_POWER (26)")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shell sleep keyevent failed: ${e.message}")
            }
        }
        return false
    }

    private suspend fun wakeAndUnlock(context: WorkflowContext, password: String?): Boolean {
        // Step 1: wake the screen
        if (!wakeScreen(context)) {
            Log.e(TAG, "wake_and_unlock: failed to wake screen")
            return false
        }

        if (shellExecutor == null) {
            Log.w(TAG, "wake_and_unlock: no shell executor, cannot perform unlock")
            return true // screen is at least awake
        }

        try {
            // Step 2: unlock via KEYCODE_MENU (82) - swipes up on lock screen
            shellExecutor.exec("input keyevent 82", asRoot = false)

            // Step 3: if password is provided, enter it
            if (password != null) {
                // Wait briefly for the unlock screen to appear
                kotlinx.coroutines.delay(500)
                shellExecutor.exec("input text \"$password\"", asRoot = false)
                kotlinx.coroutines.delay(200)
                // Press Enter to confirm
                shellExecutor.exec("input keyevent 66", asRoot = false)
            }

            Log.i(TAG, "Screen woken and unlocked successfully")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Shell unlock sequence failed: ${e.message}")
            return false
        }
    }

    companion object {
        private const val TAG = "HabitatScreenWake"
    }
}
