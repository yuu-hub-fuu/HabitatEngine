package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [ACTION_FILE_OPERATION] — 文件操作 (read/write/delete/append/list/exists).
 *
 * params:
 * - `action` (必填): "read" / "write" / "delete" / "append" / "list" / "exists"
 * - `path` (必填): 文件/目录路径，支持 `${var}` 插值
 * - `content` (write/append 时需要): 要写入的内容
 * - `encoding` (可选): 默认 "utf-8"
 * - `output_var` (可选): 输出变量名，默认 "file_content"
 *
 * 安全限制:
 * - 禁止操作 /system、/data/data (非自身) 等系统路径
 * - delete 操作记录到日志
 *
 * 输出变量:
 * - `file_success` (Boolean)
 * - `file_content` / `output_var` (String) — read 的内容
 * - `file_exists` (Boolean) — exists 的结果
 * - `file_list` (String) — list 的结果 (JSON array)
 * - `file_error` (String) — 错误信息
 */
class NodeFileOperationHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val action = params["action"]?.toString()?.trim()?.lowercase()
            ?: return NodeResult.failure(node.next, "Missing 'action' parameter",
                mapOf("file_success" to false))

        val rawPath = params["path"]?.toString()?.trim()
            ?: return NodeResult.failure(node.next, "Missing 'path' parameter",
                mapOf("file_success" to false))

        val path = context.interpolate(rawPath)

        // Security: block system paths
        if (isBlockedPath(path)) {
            context.log("FILE_OPERATION BLOCKED: $path is a restricted path")
            return NodeResult.failure(node.next, "Access to '$path' is restricted",
                mapOf("file_success" to false, "file_error" to "restricted_path"))
        }

        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null } ?: "file_content"
        val encoding = params["encoding"]?.toString()?.trim() ?: "utf-8"
        val content = params["content"]?.toString()?.let { context.interpolate(it) }

        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "read" -> handleRead(path, encoding, outputVar, node, context)
                    "write" -> handleWrite(path, content, encoding, node, context)
                    "append" -> handleAppend(path, content, encoding, node, context)
                    "delete" -> handleDelete(path, node, context)
                    "list" -> handleList(path, outputVar, node, context)
                    "exists" -> handleExists(path, outputVar, node, context)
                    "mkdir" -> handleMkdir(path, node, context)
                    else -> NodeResult.failure(node.next, "Unknown action: $action",
                        mapOf("file_success" to false, "file_error" to "unknown_action"))
                }
            } catch (e: SecurityException) {
                NodeResult.failure(node.next, "Permission denied: ${e.message}",
                    mapOf("file_success" to false, "file_error" to "permission_denied"))
            } catch (e: Exception) {
                NodeResult.failure(node.next, "File error: ${e.message}",
                    mapOf("file_success" to false, "file_error" to (e.message ?: "Unknown")))
            }
        }
    }

    private fun handleRead(path: String, encoding: String, outputVar: String,
                           node: WorkflowNode, context: WorkflowContext): NodeResult {
        val file = File(path)
        if (!file.exists()) {
            context.log("FILE_READ: $path does not exist")
            return NodeResult.failure(node.next, "File not found: $path",
                mapOf("file_success" to false, "file_error" to "not_found"))
        }
        if (!file.isFile) {
            context.log("FILE_READ: $path is not a file")
            return NodeResult.failure(node.next, "Not a file: $path",
                mapOf("file_success" to false, "file_error" to "not_a_file"))
        }
        val text = file.readText(charset(encoding))
        context.log("FILE_READ: $path → ${text.length} chars")
        return NodeResult.success(node.next, mapOf(
            outputVar to text, "file_content" to text, "file_success" to true,
        ))
    }

    private fun handleWrite(path: String, content: String?, encoding: String,
                            node: WorkflowNode, context: WorkflowContext): NodeResult {
        if (content == null) return NodeResult.failure(node.next, "Missing 'content' for write",
            mapOf("file_success" to false))
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content, charset(encoding))
        context.log("FILE_WRITE: $path ← ${content.length} chars")
        return NodeResult.success(node.next, mapOf("file_success" to true))
    }

    private fun handleAppend(path: String, content: String?, encoding: String,
                             node: WorkflowNode, context: WorkflowContext): NodeResult {
        if (content == null) return NodeResult.failure(node.next, "Missing 'content' for append",
            mapOf("file_success" to false))
        val file = File(path)
        file.parentFile?.mkdirs()
        file.appendText(content, charset(encoding))
        context.log("FILE_APPEND: $path +${content.length} chars")
        return NodeResult.success(node.next, mapOf("file_success" to true))
    }

    private fun handleDelete(path: String, node: WorkflowNode, context: WorkflowContext): NodeResult {
        val file = File(path)
        if (!file.exists()) {
            context.log("FILE_DELETE: $path does not exist (no-op)")
            return NodeResult.success(node.next, mapOf("file_success" to true))
        }
        val deleted = file.delete()
        context.log("FILE_DELETE: $path → deleted=$deleted")
        return if (deleted) {
            NodeResult.success(node.next, mapOf("file_success" to true))
        } else {
            NodeResult.failure(node.next, "Failed to delete: $path",
                mapOf("file_success" to false, "file_error" to "delete_failed"))
        }
    }

    private fun handleList(path: String, outputVar: String,
                           node: WorkflowNode, context: WorkflowContext): NodeResult {
        val dir = File(path)
        if (!dir.exists()) return NodeResult.failure(node.next, "Directory not found: $path",
            mapOf("file_success" to false, "file_error" to "not_found"))
        if (!dir.isDirectory) return NodeResult.failure(node.next, "Not a directory: $path",
            mapOf("file_success" to false))

        val items = dir.listFiles()?.sortedBy { it.name }?.map { file ->
            """{"name":"${file.name}","type":"${if (file.isDirectory) "dir" else "file"}",""" +
                """"size":${file.length()},"modified":${file.lastModified()}}"""
        } ?: emptyList()
        val json = "[${items.joinToString(",")}]"
        context.log("FILE_LIST: $path → ${items.size} items")
        return NodeResult.success(node.next, mapOf(
            outputVar to json, "file_list" to json, "file_success" to true,
        ))
    }

    private fun handleExists(path: String, outputVar: String,
                             node: WorkflowNode, context: WorkflowContext): NodeResult {
        val exists = File(path).exists()
        context.log("FILE_EXISTS: $path → $exists")
        return NodeResult.success(node.next, mapOf(
            outputVar to exists, "file_exists" to exists, "file_success" to true,
        ))
    }

    private fun handleMkdir(path: String, node: WorkflowNode, context: WorkflowContext): NodeResult {
        val created = File(path).mkdirs()
        context.log("FILE_MKDIR: $path → created=$created")
        return if (created) NodeResult.success(node.next, mapOf("file_success" to true))
        else NodeResult.failure(node.next, "Failed to create directory: $path",
            mapOf("file_success" to false))
    }

    private fun isBlockedPath(path: String): Boolean {
        val normalized = path.lowercase().replace('\\', '/')
        val blockedPrefixes = listOf(
            "/system/", "/data/system/", "/dev/", "/proc/", "/sys/",
        )
        if (normalized == "/data" || normalized.startsWith("/data/data/")) {
            return true
        }
        return blockedPrefixes.any { normalized.startsWith(it) }
    }

    private fun charset(encoding: String) = try { java.nio.charset.Charset.forName(encoding) }
        catch (_: Exception) { Charsets.UTF_8 }

    companion object {
        // File operation handler — full implementation
    }
}
