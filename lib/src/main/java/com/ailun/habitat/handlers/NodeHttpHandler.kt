package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_HTTP_REQUEST] — 执行 HTTP 请求。
 *
 * FIXME: Currently a stub. Real HTTP implementation pending OkHttp integration.
 * When implemented, will support GET/POST/PUT/DELETE with headers, body, and
 * response parsing.
 */
class NodeHttpHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val msg = "ACTION_HTTP_REQUEST is not yet implemented (stub)"
        context.log("WARNING: $msg")
        return NodeResult.failure(
            next = node.branches?.get("error") ?: node.next,
            error = msg,
            vars = mapOf("http_success" to false, "http_status_code" to 0),
        )
    }
}
