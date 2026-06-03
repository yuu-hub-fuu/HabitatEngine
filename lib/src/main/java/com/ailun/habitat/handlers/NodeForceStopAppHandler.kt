package com.ailun.habitat.handlers

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_FORCE_STOP_APP]：强制停止指定应用。
 *
 * params：`package_name`（必填）
 */
class NodeForceStopAppHandler(
    private val provider: IAccessibilityProvider? = null,
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val packageName = node.params?.get("package_name")?.toString()?.trim().orEmpty()
        if (packageName.isEmpty()) {
            Log.e(TAG, "Force stop failed: 'package_name' parameter is empty")
            context.variables["force_stop_success"] = false
            return node.nextResult()
        }

        var success = false

        // Primary: use shell via IShellExecutor
        if (shellExecutor != null) {
            try {
                val command = "am force-stop $packageName"
                val output = shellExecutor.exec(command, asRoot = false)
                Log.i(TAG, "Shell force-stop output (normal): $output")
                success = !output.contains("Error", ignoreCase = true) &&
                        !output.contains("SecurityException", ignoreCase = true)
            } catch (e: Exception) {
                Log.w(TAG, "Shell force-stop (normal) failed: ${e.message}")
            }
        }

        // Fallback: try shell as root
        if (!success && shellExecutor != null) {
            try {
                val command = "am force-stop $packageName"
                val output = shellExecutor.exec(command, asRoot = true)
                Log.i(TAG, "Shell force-stop output (root): $output")
                success = !output.contains("Error", ignoreCase = true) &&
                        !output.contains("SecurityException", ignoreCase = true)
            } catch (e: Exception) {
                Log.w(TAG, "Shell force-stop (root) failed: ${e.message}")
            }
        }

        // API 33+ fallback: use ActivityManager directly
        if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val activityManager = context.appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (activityManager != null) {
                    // Use reflection to call forceStopPackage (may be restricted)
                    try {
                        val method = ActivityManager::class.java.getMethod("forceStopPackage", String::class.java)
                        method.invoke(activityManager, packageName)
                        success = true
                        Log.i(TAG, "ActivityManager force-stop succeeded for '$packageName' via reflection")
                    } catch (e: Exception) {
                        Log.w(TAG, "ActivityManager force-stop via reflection failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ActivityManager force-stop failed: ${e.message}")
            }
        }

        context.variables["force_stop_success"] = success
        if (success) {
            Log.i(TAG, "Successfully force-stopped app: $packageName")
        } else {
            Log.e(TAG, "Failed to force-stop app: $packageName")
        }

        return node.nextResult()
    }

    companion object {
        private const val TAG = "HabitatForceStopApp"
    }
}
