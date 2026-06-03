package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_SHELL]：执行 shell 命令。
 *
 * params：
 * - `command`（必填）：要执行的命令
 * - `mode`（可选）：`auto` | `shizuku` | `root`（默认 auto）
 * - `timeout_ms`（可选）：超时毫秒数（默认 30_000）
 *
 * **Security:** Requires a real [IShellExecutor] (e.g. Shizuku). There is no
 * silent fallback to `Runtime.exec()` — if no executor is provided the node
 * fails explicitly so callers are forced to grant proper permissions.
 */
class NodeShellHandler(
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val rawCommand = node.params?.get("command")?.toString()?.trim().orEmpty()
        if (rawCommand.isEmpty()) return node.nextResult()

        val command = context.interpolate(rawCommand)
        val modeStr = node.params?.get("mode")?.toString()?.trim()?.lowercase() ?: MODE_AUTO
        val asRoot = modeStr == MODE_ROOT
        val timeoutMs = (node.params?.get("timeout_ms") as? Number)?.toLong()?.coerceIn(1_000, 300_000) ?: 30_000L

        val executor = shellExecutor
        if (executor != null) {
            val output = executor.exec(command, asRoot)
            val success = !output.startsWith("Error:")
            context.variables["shell_output"] = output
            context.variables["shell_success"] = success
            context.variables["shell_exit_code"] = if (success) 0 else -1
            context.log("Shell [$modeStr]: command=${command.take(60)}... → exit=${if (success) 0 else -1}")
        } else {
            // Shell execution requires Shizuku or equivalent IShellExecutor.
            // No silent Runtime.exec fallback — callers must grant proper permissions.
            val msg = "No IShellExecutor available; grant Shizuku permission to run shell commands"
            context.variables["shell_output"] = "Error: $msg"
            context.variables["shell_success"] = false
            context.variables["shell_exit_code"] = -1
            context.variables["_last_error"] = true
            context.variables["_last_error_msg"] = msg
            context.log("Shell [$modeStr]: $msg")
        }

        return node.nextResult()
    }

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_SHIZUKU = "shizuku"
        const val MODE_ROOT = "root"
    }
}
