package com.ailun.habitat.handlers

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_BRIGHTNESS]：控制屏幕亮度及获取亮度信息。
 */
class NodeBrightnessHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Brightness failed: 'action' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'action' parameter")
        }

        val contentResolver = context.appContext.contentResolver
        val outVars = mutableMapOf<String, Any?>()

        try {
            when (action) {
                "set" -> handleSet(node, context, contentResolver, outVars)
                "get" -> handleGet(contentResolver, outVars)
                "auto_on" -> handleAutoMode(contentResolver, true, outVars)
                "auto_off" -> handleAutoMode(contentResolver, false, outVars)
                else -> return NodeResult.failure(node.next, "Unknown action: $action")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Brightness: missing WRITE_SETTINGS permission - ${e.message}")
            return NodeResult.failure(node.next, "Permission denied: ${e.message}", outVars)
        } catch (e: Exception) {
            Log.e(TAG, "Brightness error for action '$action': ${e.message}", e)
            return NodeResult.failure(node.next, "Brightness error: ${e.message}", outVars)
        }

        return NodeResult.success(node.next, outVars)
    }

    private fun handleSet(
        node: WorkflowNode, context: WorkflowContext,
        contentResolver: ContentResolver, out: MutableMap<String, Any?>,
    ) {
        val brightness = when (val v = node.params?.get("value")) {
            is Number -> v.toInt()
            else -> v?.toString()?.toIntOrNull()
        } ?: run { Log.e(TAG, "Brightness set: missing 'value'"); return }
        val clamped = brightness.coerceIn(0, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.System.canWrite(context.appContext)
        ) {
            Log.w(TAG, "Brightness set: no WRITE_SETTINGS permission")
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.appContext.startActivity(intent)
            } catch (e: Exception) { Log.e(TAG, "Failed to launch settings intent: ${e.message}") }
        }

        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
        out["brightness_value"] = clamped
        Log.i(TAG, "Brightness set to $clamped (0-255)")
    }

    private fun handleGet(contentResolver: ContentResolver, out: MutableMap<String, Any?>) {
        val brightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        val mode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        out["brightness_value"] = brightness
        out["brightness_mode"] = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) "auto" else "manual"
        Log.i(TAG, "Brightness get: value=$brightness, mode=${out["brightness_mode"]}")
    }

    private fun handleAutoMode(
        contentResolver: ContentResolver, enable: Boolean, out: MutableMap<String, Any?>,
    ) {
        val mode = if (enable) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        val label = if (enable) "auto" else "manual"
        out["brightness_mode"] = label
        Log.i(TAG, "Brightness mode set to $label")
    }

    companion object { private const val TAG = "HabitatBrightness" }
}
