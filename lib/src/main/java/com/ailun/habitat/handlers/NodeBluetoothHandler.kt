package com.ailun.habitat.handlers

import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_BLUETOOTH]：控制蓝牙开关及获取状态。
 *
 * params：`action`（必填："on"/"off"/"toggle"/"status"）
 */
class NodeBluetoothHandler(
    private val provider: IAccessibilityProvider? = null,
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Bluetooth failed: 'action' parameter is empty")
            context.variables["bluetooth_success"] = false
            return node.nextResult()
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth failed: device does not support Bluetooth")
            context.variables["bluetooth_success"] = false
            return node.nextResult()
        }

        var success = false

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
                    if (bluetoothAdapter.isEnabled) {
                        @Suppress("DEPRECATION")
                        success = bluetoothAdapter.disable()
                    } else {
                        @Suppress("DEPRECATION")
                        success = bluetoothAdapter.enable()
                    }
                    if (success) {
                        @Suppress("DEPRECATION")
                        Log.i(TAG, "Bluetooth toggled to ${!bluetoothAdapter.isEnabled}")
                    } else {
                        Log.e(TAG, "Bluetooth toggle failed")
                    }
                }
                "status" -> {
                    @Suppress("DEPRECATION")
                    val enabled = bluetoothAdapter.isEnabled
                    context.variables["bluetooth_enabled"] = enabled
                    success = true
                    Log.i(TAG, "Bluetooth status: enabled=$enabled")
                }
                else -> {
                    Log.e(TAG, "Bluetooth failed: unknown action '$action'")
                    context.variables["bluetooth_success"] = false
                    return node.nextResult()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth error for action '$action': ${e.message}", e)
            success = false
        }

        context.variables["bluetooth_success"] = success
        return node.nextResult()
    }

    companion object {
        private const val TAG = "HabitatBluetooth"
    }
}
