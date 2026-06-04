package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_FILE_OPERATION] — 文件操作 (read/write/delete).
 *
 * FIXME: Currently a stub. Real file I/O implementation pending.
 * When implemented, will check file permissions, support text/binary modes,
 * and enforce path restrictions.
 */
class NodeFileOperationHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val msg = "ACTION_FILE_OPERATION is not yet implemented (stub)"
        context.log("WARNING: $msg")
        return NodeResult.failure(
            next = node.branches?.get("error") ?: node.next,
            error = msg,
            vars = mapOf("file_success" to false),
        )
    }
}
