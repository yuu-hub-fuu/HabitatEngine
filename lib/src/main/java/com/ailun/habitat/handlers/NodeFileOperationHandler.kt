package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import java.io.File

/**
 * [ACTION_FILE_OPERATION]：文件操作（读、写、删除、存在检查、列表）。
 *
 * params：
 * - `action`（必填）："read" / "write" / "delete" / "exists" / "list"
 * - `path`（必填）：文件或目录路径
 * - `content`（write 时必填）：要写入的内容
 * - `output_var`（可选）：存储结果的变量名
 * - `append`（write 时可选）：true 表示追加，默认 false 覆盖
 * - `allow_delete_dir`（可选）：delete 时设为 true 才允许删除目录，默认拒绝
 *
 * **Security:** Operations outside the app-private directory require explicit
 * path validation. Traversal attacks (..), system paths, and other packages'
 * private data are rejected by default.
 */
class NodeFileOperationHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return node.nextResult()

        val action = params["action"]?.toString()?.trim()?.lowercase() ?: run {
            Log.w(TAG, "No action specified")
            fail(context, "No action specified")
            return node.nextResult()
        }

        val rawPath = params["path"]?.toString()?.trim() ?: run {
            Log.w(TAG, "No path specified")
            fail(context, "No path specified")
            return node.nextResult()
        }

        val path = context.interpolate(rawPath)
        val outputVar = params["output_var"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        // ── Path validation ──
        val canonicalPath = try { File(path).canonicalPath } catch (_: Exception) { path }
        val validationError = validatePath(canonicalPath, action)
        if (validationError != null) {
            Log.w(TAG, "Path rejected: $canonicalPath — $validationError")
            fail(context, validationError)
            return node.nextResult()
        }

        try {
            when (action) {
                "read" -> doRead(canonicalPath, outputVar, context)
                "write" -> doWrite(canonicalPath, params, context)
                "delete" -> doDelete(canonicalPath, params, context)
                "exists" -> doExists(canonicalPath, context)
                "list" -> doList(canonicalPath, outputVar, context)
                else -> fail(context, "Unknown action: $action")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "File operation permission denied: ${e.message}", e)
            fail(context, "Permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "File operation failed: ${e.message}", e)
            fail(context, e.message ?: "Unknown error")
        }

        return node.nextResult()
    }

    // ── Path validation ──

    private fun validatePath(path: String, action: String): NodeResult {
        // Traversal protection.
        if (path.contains("..")) return "Traversal detected in path"

        // Write/delete must not touch system or other-package data.
        if (action in setOf("write", "delete")) {
            for (forbidden in FORBIDDEN_WRITE_PREFIXES) {
                if (path.startsWith(forbidden)) return "Write/delete to $forbidden is forbidden"
            }
        }

        // Directory delete requires explicit allow flag (checked at call site).
        // Read/write to /sdcard/Android/* is blocked regardless.
        if (path.startsWith("/sdcard/Android/") || path.startsWith("/storage/emulated/0/Android/")) {
            if (action in setOf("write", "delete")) return "Cannot modify other apps' private data"
        }

        return null
    }

    // ── Actions ──

    private fun doRead(path: String, outputVar: String?, context: WorkflowContext) {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            fail(context, "File does not exist or is not a file")
            return
        }
        val content = file.readText(Charsets.UTF_8)
        val resultKey = outputVar ?: "file_result"
        context.variables["file_result"] = content
        context.variables[resultKey] = content
        context.variables["file_success"] = true
        context.variables["file_size"] = content.length
        Log.i(TAG, "File read: $path (${content.length} chars)")
    }

    private fun doWrite(path: String, params: Map<String, Any?>, context: WorkflowContext) {
        val rawContent = params["content"]?.toString() ?: ""
        val content = context.interpolate(rawContent)
        val append = params["append"]?.toString()?.equals("true", true) == true

        val file = File(path)
        // Auto-create parent directories only inside allowed paths.
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                if (!parent.mkdirs()) {
                    fail(context, "Failed to create parent directories")
                    return
                }
            }
        }

        if (append) file.appendText(content, Charsets.UTF_8)
        else file.writeText(content, Charsets.UTF_8)

        context.variables["file_result"] = path
        context.variables["file_success"] = true
        context.variables["file_size"] = content.length
        Log.i(TAG, "File written: $path (${content.length} chars, append=$append)")
    }

    private fun doDelete(path: String, params: Map<String, Any?>, context: WorkflowContext) {
        val file = File(path)
        if (!file.exists()) {
            fail(context, "File does not exist")
            return
        }

        if (file.isDirectory) {
            val allowDeleteDir = params["allow_delete_dir"]?.toString()?.equals("true", true) == true
            if (!allowDeleteDir) {
                fail(context, "Directory deletion requires allow_delete_dir=true")
                return
            }
        }

        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        context.variables["file_result"] = deleted
        context.variables["file_success"] = deleted
        if (!deleted) context.variables["file_error"] = "Deletion failed"
        Log.i(TAG, "File delete: $path -> $deleted")
    }

    private fun doExists(path: String, context: WorkflowContext) {
        val file = File(path)
        context.variables["file_result"] = file.exists()
        context.variables["file_exists"] = file.exists()
        context.variables["file_is_directory"] = file.isDirectory
        context.variables["file_is_file"] = file.isFile
        context.variables["file_success"] = true
        Log.i(TAG, "File exists: $path -> exists=${file.exists()}")
    }

    private fun doList(path: String, outputVar: String?, context: WorkflowContext) {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            fail(context, "Directory does not exist or is not a directory")
            return
        }
        val children = file.listFiles()
        val names = children?.map { c ->
            "${if (c.isDirectory) "[D]" else "[F]"} ${c.name}"
        }?.sorted().orEmpty()

        val jsonArray = names.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
        context.variables["file_result"] = "[$jsonArray]"
        context.variables["file_list"] = names
        context.variables["file_count"] = names.size
        context.variables["file_success"] = true
        outputVar?.let { context.variables[it] = "[$jsonArray]" }
        Log.i(TAG, "File list: $path -> ${names.size} entries")
    }

    private fun fail(context: WorkflowContext, error: String) {
        context.variables["file_success"] = false
        context.variables["file_error"] = error
    }

    companion object {
        private const val TAG = "HabitatFileOp"

        /** Paths that write/delete operations must never touch. */
        private val FORBIDDEN_WRITE_PREFIXES = listOf(
            "/system", "/sys", "/proc", "/dev",
            "/data/data", "/data/user",
            "/data/system", "/data/app",
            "/vendor", "/product", "/odm",
        )
    }
}
