package com.ailun.habitat.handlers

import android.content.Intent
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_LAUNCH_APP]：启动指定应用。
 *
 * params：`package_name`（必填），`activity`（可选）
 */
class NodeLaunchAppHandler(
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val packageName = node.params?.get("package_name")?.toString()?.trim().orEmpty()
        if (packageName.isEmpty()) {
            Log.e(TAG, "Launch app failed: 'package_name' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'package_name' parameter",
                mapOf("launch_success" to false))
        }

        val activityName = node.params?.get("activity")?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }

        try {
            val intent = if (activityName != null) {
                Intent().apply {
                    setClassName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                context.appContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            if (intent == null) {
                Log.e(TAG, "Launch app failed: unable to resolve intent for package '$packageName'")
                return NodeResult.failure(node.next, "Unable to resolve intent for '$packageName'",
                    mapOf("launch_success" to false))
            }

            context.appContext.startActivity(intent)
            Log.i(TAG, "Successfully launched app: $packageName")
            return NodeResult.success(node.next, mapOf("launch_success" to true))
        } catch (e: Exception) {
            Log.e(TAG, "Launch app error for '$packageName': ${e.message}", e)
            return NodeResult.failure(node.next, "Launch failed: ${e.message}",
                mapOf("launch_success" to false))
        }
    }

    companion object {
        private const val TAG = "HabitatLaunchApp"
    }
}
