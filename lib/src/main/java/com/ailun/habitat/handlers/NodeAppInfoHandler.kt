package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider

/**
 * [ACTION_GET_APP_INFO]：获取当前前台应用包名和 Activity。
 */
class NodeAppInfoHandler(
    private val a11yProvider: IAccessibilityProvider? = null
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val outputVar = node.params?.get("output_var")?.toString()?.trim()
            ?.ifEmpty { null } ?: "current_app"
        val activityVar = node.params?.get("activity_var")?.toString()?.trim()
            ?.ifEmpty { null } ?: "current_activity"

        val provider = a11yProvider
        val packageName: String
        val className: String

        if (provider != null) {
            packageName = provider.foregroundPackage ?: "unknown"
            className = provider.foregroundActivity ?: "unknown"
        } else {
            // Fallback: use accessibility service directly
            val service = provider?.getService()
            packageName = service?.rootInActiveWindow?.let { root ->
                try { root.packageName?.toString() ?: "unknown" }
                finally { root.recycle() }
            } ?: "unknown"
            className = "unknown"
        }

        context.putVariable(outputVar, packageName)
        context.putVariable(activityVar, className)
        context.log("AppInfo: $packageName / $className")
        return node.nextResult()
    }
}
