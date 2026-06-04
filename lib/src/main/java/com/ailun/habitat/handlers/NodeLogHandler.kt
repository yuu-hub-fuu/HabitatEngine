package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_LOG]：输出调试日志，支持变量插值。
 *
 * params：
 * - `message`：日志内容，支持 ${var_name} 格式的变量插值
 * - `level`：可选，`debug`|`info`|`warn`|`error`，默认 info
 */
class NodeLogHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.success(node.next)
        
        val rawMessage = params["message"]?.toString() ?: ""
        val level = params["level"]?.toString()?.trim()?.lowercase() ?: "info"
        
        // 使用 Context 统一的插值逻辑
        val message = context.interpolate(rawMessage)
        
        log(level, TAG, message)
        return NodeResult.success(node.next)
    }
    
    private fun log(level: String, tag: String, msg: String) {
        when (level) {
            "debug" -> Log.d(tag, msg)
            "info" -> Log.i(tag, msg)
            "warn" -> Log.w(tag, msg)
            "error" -> Log.e(tag, msg)
            else -> Log.i(tag, msg)
        }
    }
    
    companion object {
        private const val TAG = "HabitatLog"
    }
}
