package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.delay

/**
 * [ACTION_DELAY]：暂停执行指定毫秒数。
 * params: `millis`（优先）或 `ms` — 延迟毫秒数
 */
class NodeDelayHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val ms = parseLong(node.params?.get("millis"))
            ?: parseLong(node.params?.get("ms"))
            ?: 0L
        context.log("Delay ${ms}ms")
        if (ms > 0) delay(ms.coerceAtMost(60_000L))
        return node.next
    }

    private fun parseLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
}
