package com.ailun.habitat.handlers

import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_BLUETOOTH]：控制蓝牙开关及获取状态。
 *
 * params：`action`（必填："on"/"off"/"toggle"/"status"）
 */
class NodeBluetoothHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Bluetooth failed: 'action' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'action' parameter",
                mapOf("bluetooth_success" to false))
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth failed: device does not support Bluetooth")
            return NodeResult.failure(node.next, "Device does not support Bluetooth",
                mapOf("bluetooth_success" to false))
        }

        var success = false
        val outputVars = mutableMapOf<String, Any?>("bluetooth_success" to false)

        try {
            when (action) {
                "on" -> {
                    @Suppress("DEPRECATION")
                    success = bluetoothAdapter.enable()
                    if (success) {
                        Log.i(TAG, "Bluetooth turned on")
                    } else {
                        Log.e(TAG, "Bluetooth failed to turn on")
                    }
                }
                "off" -> {
                    @Suppress("DEPRECATION")
                    success = bluetoothAdapter.disable()
                    if (success) {
                        Log.i(TAG, "Bluetooth turned off")
                    } else {
                        Log.e(TAG, "Bluetooth failed to turn off")
                    }
                }
                "toggle" -> {
                    @Suppress("DEPRECATION")
                    val wasEnabled = bluetoothAdapter.isEnabled
                    if (wasEnabled) {
                        @Suppress("DEPRECATION")
                        success = bluetoothAdapter.disable()
                    } else {
                        @Suppress("DEPRECATION")
                        success = bluetoothAdapter.enable()
                    }
                    if (success) {
                        Log.i(TAG, "Bluetooth toggled from $wasEnabled to ${!wasEnabled}")
                    } else {
                        Log.e(TAG, "Bluetooth toggle failed")
                    }
                }
                "status" -> {
                    @Suppress("DEPRECATION")
                    val enabled = bluetoothAdapter.isEnabled
                    outputVars["bluetooth_enabled"] = enabled
                    success = true
                    Log.i(TAG, "Bluetooth status: enabled=$enabled")
                }
                else -> {
                    Log.e(TAG, "Bluetooth failed: unknown action '$action'")
                    return NodeResult.failure(node.next, "Unknown action: $action",
                        mapOf("bluetooth_success" to false))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth error for action '$action': ${e.message}", e)
            success = false
        }

        outputVars["bluetooth_success"] = success
        return if (success) {
            NodeResult.success(node.next, outputVars)
        } else {
            NodeResult.failure(node.next, "Bluetooth action '$action' failed", outputVars)
        }
    }

    companion object {
        private const val TAG = "HabitatBluetooth"
    }
}
