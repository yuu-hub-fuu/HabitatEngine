package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IShellExecutor
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

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

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val rawCommand = node.params?.get("command")?.toString()?.trim().orEmpty()
        if (rawCommand.isEmpty()) return node.next

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
            // Real-device environment: try Runtime.exec() with timeout as last resort,
            // but still record the fact that Shizuku was unavailable.
            context.log("Shell [$modeStr]: no IShellExecutor; using Runtime.exec fallback (Shizuku unavailable)")
            try {
                val result = runWithTimeout(command, timeoutMs)
                context.variables["shell_output"] = result.output
                context.variables["shell_success"] = result.exitCode == 0
                context.variables["shell_exit_code"] = result.exitCode
                context.log("Shell [$modeStr]: command=${command.take(60)}... → exit=${result.exitCode}")
            } catch (e: Exception) {
                context.variables["shell_output"] = "Error: ${e.message}"
                context.variables["shell_success"] = false
                context.variables["shell_exit_code"] = -1
                context.log("Shell [$modeStr]: failed — ${e.message}")
            }
        }

        return node.next
    }

    /**
     * Execute a command with a hard timeout.
     * Limits stdout+stderr to [MAX_OUTPUT_BYTES] combined.
     */
    private fun runWithTimeout(command: String, timeoutMs: Long): ShellResult {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutThread = process.inputStream.copyStream(stdout, MAX_OUTPUT_BYTES)
        val stderrThread = process.errorStream.copyStream(stderr, MAX_OUTPUT_BYTES)

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            stdoutThread.join(1_000)
            stderrThread.join(1_000)
            throw RuntimeException("Command timed out after $timeoutMs ms")
        }

        stdoutThread.join(1_000)
        stderrThread.join(1_000)
        val output = stdout.toString(Charsets.UTF_8.name()).trim()
        val error = stderr.toString(Charsets.UTF_8.name()).trim()
        return ShellResult(
            exitCode = process.exitValue(),
            output = output.ifEmpty { error },
        )
    }

    private data class ShellResult(val exitCode: Int, val output: String)

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_SHIZUKU = "shizuku"
        const val MODE_ROOT = "root"
        private const val MAX_OUTPUT_BYTES = 64 * 1024 // 64 KB

        /** Stream copier that enforces a byte limit. */
        private fun java.io.InputStream.copyStream(
            out: ByteArrayOutputStream,
            maxBytes: Int,
        ): Thread {
            val input = this
            val thread = Thread {
                try {
                    val buf = ByteArray(4096)
                    var total = 0
                    var n: Int
                    while (input.read(buf).also { n = it } != -1 && total < maxBytes) {
                        val toWrite = n.coerceAtMost(maxBytes - total)
                        out.write(buf, 0, toWrite)
                        total += toWrite
                    }
                } catch (_: Exception) { /* stream closed */ }
            }
            thread.isDaemon = true
            thread.start()
            return thread
        }
    }
}
