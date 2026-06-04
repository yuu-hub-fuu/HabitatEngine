package com.ailun.habitat.handlers

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

class NodeWifiHandler(
    private val provider: IAccessibilityProvider? = null,
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "WiFi failed: 'action' parameter is empty")
            context.variables["wifi_success"] = false
            return NodeResult.success(node.next)
        }

        @Suppress("DEPRECATION")
        val wifiManager = context.appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            Log.e(TAG, "WiFi failed: unable to get WifiManager service")
            context.variables["wifi_success"] = false
            return NodeResult.success(node.next)
        }

        var success = false

        try {
            when (action) {
                "on" -> {
                    success = setWifiEnabled(context, wifiManager, true)
                    Log.i(TAG, "WiFi turn on: success=$success")
                }
                "off" -> {
                    success = setWifiEnabled(context, wifiManager, false)
                    Log.i(TAG, "WiFi turn off: success=$success")
                }
                "toggle" -> {
                    @Suppress("DEPRECATION")
                    val currentlyEnabled = wifiManager.isWifiEnabled
                    success = setWifiEnabled(context, wifiManager, !currentlyEnabled)
                    Log.i(TAG, "WiFi toggle to ${!currentlyEnabled}: success=$success")
                }
                "status" -> {
                    // Give async WiFi state time to settle before reading
                    kotlinx.coroutines.delay(500)
                    @Suppress("DEPRECATION")
                    val state = wifiManager.wifiState
                    val enabled = state == WifiManager.WIFI_STATE_ENABLED ||
                            state == WifiManager.WIFI_STATE_ENABLING
                    context.variables["wifi_enabled"] = enabled
                    context.variables["wifi_state"] = state
                    success = true
                    Log.i(TAG, "WiFi status: enabled=$enabled, state=$state")
                }
                else -> {
                    Log.e(TAG, "WiFi failed: unknown action '$action'")
                    context.variables["wifi_success"] = false
                    return NodeResult.success(node.next)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WiFi error for action '$action': ${e.message}", e)
            success = false
        }

        context.variables["wifi_success"] = success
        return NodeResult.success(node.next)
    }

    private suspend fun setWifiEnabled(context: WorkflowContext, wifiManager: WifiManager, enable: Boolean): Boolean {
        // Strategy 1: shell command (svc wifi) — works on most devices without special permission
        val shell = shellExecutor
        if (shell != null) {
            try {
                val cmd = if (enable) "svc wifi enable" else "svc wifi disable"
                val result = shell.exec(cmd, asRoot = false)
                Log.i(TAG, "Shell $cmd → $result")
                if (result.isNotBlank() && !result.contains("Error", ignoreCase = true)) {
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shell WiFi command failed, falling back to WifiManager: ${e.message}")
            }
        }

        // Strategy 2: deprecated WifiManager API (may silently fail on Android 10+)
        @Suppress("DEPRECATION")
        val apiResult = wifiManager.setWifiEnabled(enable)
        if (apiResult) return true

        // Strategy 3: on Android 10+, the API is restricted — log the likely cause
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.w(
                TAG,
                "WiFi setWifiEnabled returned false. This API is restricted on Android 10+." +
                        " Ensure CHANGE_WIFI_STATE permission is granted or use Shell/Shizuku."
            )
        }
        return false
    }

    companion object {
        private const val TAG = "HabitatWifi"
    }
}
