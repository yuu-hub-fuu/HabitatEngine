package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.RuntimeVars
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.delay

/**
 * [ACTION_DELAY]：暂停执行指定毫秒数。
 *
 * params:
 * - `millis`（优先）或 `ms` — 延迟毫秒数
 *   Must be >= 0. Negative values fail. Values above [MAX_DELAY_MS]
 *   are capped and the truncation is logged.
 */
class NodeDelayHandler : INodeHandler {

    companion object {
        const val MAX_DELAY_MS = 600_000L // 10 minutes
    }

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val raw = node.params?.get("millis") ?: node.params?.get("ms")

        val ms = when (raw) {
            is Number -> raw.toLong()
            is String -> {
                raw.toLongOrNull() ?: run {
                    val msg = "Invalid delay value: $raw"
                    context.log("ACTION_DELAY: $msg")
                    return NodeResult.failure(node.next, msg)
                }
            }
            else -> {
                val msg = "Missing delay parameter"
                context.log("ACTION_DELAY: $msg")
                return NodeResult.failure(node.next, msg)
            }
        }

        if (ms < 0) {
            val msg = "Negative delay: $ms"
            context.log("ACTION_DELAY: $msg")
            return NodeResult.failure(node.next, msg)
        }

        val effective = if (ms > MAX_DELAY_MS) {
            context.log("ACTION_DELAY: requested ${ms}ms capped to ${MAX_DELAY_MS}ms (${
                ms / 60_000
            } min → ${MAX_DELAY_MS / 60_000} min max)")
            MAX_DELAY_MS
        } else {
            ms
        }

        if (effective > 0) delay(effective)
        return NodeResult.success(node.next, mapOf("delay_truncated" to (ms > MAX_DELAY_MS)))
    }
}
