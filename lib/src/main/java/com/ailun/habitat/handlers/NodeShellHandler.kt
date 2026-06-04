package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.RuntimeVars
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
        if (rawCommand.isEmpty()) return NodeResult.success(node.next)

        val command = context.interpolate(rawCommand)
        val modeStr = node.params?.get("mode")?.toString()?.trim()?.lowercase() ?: MODE_AUTO
        val asRoot = modeStr == MODE_ROOT
        val timeoutMs = (node.params?.get("timeout_ms") as? Number)?.toLong()?.coerceIn(1_000, 300_000) ?: 30_000L

        val executor = shellExecutor
        if (executor != null) {
            val output = executor.exec(command, asRoot)
            val success = !output.startsWith("Error:")
            val exitCode = if (success) 0 else -1
            return NodeResult.success(
                next = node.next,
                vars = mapOf(
                    RuntimeVars.SHELL_OUTPUT to output,
                    RuntimeVars.SHELL_SUCCESS to success,
                    RuntimeVars.SHELL_EXIT_CODE to exitCode,
                )
            ).also {
                context.log("Shell [$modeStr]: command=${command.take(60)}... → exit=$exitCode")
            }
        } else {
            // Shell execution requires Shizuku or equivalent IShellExecutor.
            // No silent Runtime.exec fallback — callers must grant proper permissions.
            val msg = "No IShellExecutor available; grant Shizuku permission to run shell commands"
            context.log("Shell [$modeStr]: $msg")
            return NodeResult.failure(
                next = node.branches?.get("error") ?: node.next,
                error = msg,
                vars = mapOf(
                    RuntimeVars.SHELL_OUTPUT to "Error: $msg",
                    RuntimeVars.SHELL_SUCCESS to false,
                    RuntimeVars.SHELL_EXIT_CODE to -1,
                )
            )
        }
    }

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_SHIZUKU = "shizuku"
        const val MODE_ROOT = "root"
    }
}
