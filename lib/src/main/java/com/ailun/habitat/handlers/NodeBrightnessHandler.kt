package com.ailun.habitat.handlers

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_BRIGHTNESS]：控制屏幕亮度及获取亮度信息。
 *
 * params：
 *   - `action`（必填："set"/"get"/"auto_on"/"auto_off"）
 *   - `value`（用于 set：0-255）
 */
class NodeBrightnessHandler(
    private val provider: IAccessibilityProvider? = null,
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Brightness failed: 'action' parameter is empty")
            return node.next
        }

        val contentResolver = context.appContext.contentResolver

        try {
            when (action) {
                "set" -> handleSet(node, context, contentResolver)
                "get" -> handleGet(context, contentResolver)
                "auto_on" -> handleAutoMode(context, contentResolver, true)
                "auto_off" -> handleAutoMode(context, contentResolver, false)
                else -> {
                    Log.e(TAG, "Brightness failed: unknown action '$action'")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Brightness error: missing WRITE_SETTINGS permission - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Brightness error for action '$action': ${e.message}", e)
        }

        return node.next
    }

    private fun handleSet(
        node: WorkflowNode,
        context: WorkflowContext,
        contentResolver: ContentResolver,
    ) {
        val valueParam = node.params?.get("value")
        val brightness = when (valueParam) {
            is Number -> valueParam.toInt()
            else -> valueParam?.toString()?.toIntOrNull()
        } ?: run {
            Log.e(TAG, "Brightness set failed: 'value' parameter is missing or invalid")
            return
        }

        val clamped = brightness.coerceIn(0, 255)

        // Check if we can write settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.System.canWrite(context.appContext)
        ) {
            Log.w(TAG, "Brightness set: no WRITE_SETTINGS permission, launching settings intent")
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.appContext.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch WRITE_SETTINGS intent: ${e.message}")
            }
        }

        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
        context.variables["brightness_value"] = clamped
        Log.i(TAG, "Brightness set to $clamped (0-255)")
    }

    private fun handleGet(
        context: WorkflowContext,
        contentResolver: ContentResolver,
    ) {
        try {
            val brightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
            val mode = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
            )

            context.variables["brightness_value"] = brightness
            context.variables["brightness_mode"] = if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                "auto"
            } else {
                "manual"
            }
            Log.i(TAG, "Brightness get: value=$brightness, mode=${context.variables["brightness_mode"]}")
        } catch (e: Exception) {
            Log.e(TAG, "Brightness get failed: ${e.message}", e)
        }
    }

    private fun handleAutoMode(
        context: WorkflowContext,
        contentResolver: ContentResolver,
        enable: Boolean,
    ) {
        try {
            val mode = if (enable) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }

            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            val modeLabel = if (enable) "auto" else "manual"
            context.variables["brightness_mode"] = modeLabel
            Log.i(TAG, "Brightness mode set to $modeLabel")
        } catch (e: Exception) {
            Log.e(TAG, "Brightness auto mode change failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "HabitatBrightness"
    }
}
