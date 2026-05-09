package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_TRY_CATCH]：错误处理与恢复（条件分支）。
 *
 * params：
 * - `try_node`：尝试执行的节点 ID
 * - `success_branch`：执行成功时的下一节点
 * - `failure_branch`：发生异常时的下一节点（可选，默认继续）
 * - `catch_var`：异常信息存储变量名（可选，默认 "exception_msg"）
 *
 * 注意：该 Handler 本身不执行节点，只管理分支选择。
 * 实际的节点执行由 HabitatExecutor 中的其他机制处理。
 *
 * 简化方案：通过 branches 字段支持错误恢复。
 * ```json
 * {
 *   "id": "try1",
 *   "type": "ACTION_TRY_CATCH",
 *   "params": {
 *     "expected_error": false,
 *     "catch_var": "exception_msg"
 *   },
 *   "branches": {
 *     "success": "next_normal",
 *     "error": "next_error_handler"
 *   }
 * }
 * ```
 */
class NodeTryCatchHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        // 查询上一步是否产生了错误标记
        val hasError = context.getVariable("_last_error") as? Boolean ?: false
        val errorMsg = context.getVariable("_last_error_msg")?.toString() ?: ""
        
        val catchVar = node.params?.get("catch_var")?.toString() ?: "exception_msg"
        if (hasError) {
            context.putVariable(catchVar, errorMsg)
        }
        
        // 清除错误标记
        context.putVariable("_last_error", false)
        context.putVariable("_last_error_msg", "")
        
        // 根据 branches 选择路径
        val key = if (hasError) "error" else "success"
        context.log("TryCatch hasError=$hasError → branch=$key")
        return node.branches?.get(key)
    }
}

