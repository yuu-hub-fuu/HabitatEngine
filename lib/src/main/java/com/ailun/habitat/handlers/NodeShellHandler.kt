package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_SHELL]：执行 shell 命令。
 * params：`command`（必填），`mode` 可选：`auto` | `shizuku` | `root`。
 */
class NodeShellHandler(
    private val shellExecutor: IShellExecutor? = null
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val rawCommand = node.params?.get("command")?.toString()?.trim().orEmpty()
        if (rawCommand.isEmpty()) return node.next

        val command = context.interpolate(rawCommand)
        val modeStr = node.params?.get("mode")?.toString()?.trim()?.lowercase() ?: MODE_AUTO
        val asRoot = modeStr == MODE_ROOT

        val shell = shellExecutor
        val output = if (shell != null) {
            shell.exec(command, asRoot)
        } else {
            runBasicShell(command)
        }

        context.variables["shell_output"] = output
        context.variables["shell_success"] = !output.startsWith("Error:")
        context.log("Shell [$modeStr]: $command → ${output.take(120)}")
        return node.next
    }

    private fun runBasicShell(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.ifEmpty { process.errorStream.bufferedReader().readText() }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_SHIZUKU = "shizuku"
        const val MODE_ROOT = "root"
    }
}
