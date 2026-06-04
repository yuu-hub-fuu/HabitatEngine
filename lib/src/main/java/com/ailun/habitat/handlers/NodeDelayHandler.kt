package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
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
                    context.log("ACTION_DELAY: invalid millis value '$raw'; failing")
                    context.variables["_last_error"] = true
                    context.variables["_last_error_msg"] = "Invalid delay value: $raw"
                    return NodeResult.success(node.next)
                }
            }
            else -> {
                context.log("ACTION_DELAY: missing or invalid millis/ms param; failing")
                context.variables["_last_error"] = true
                context.variables["_last_error_msg"] = "Missing delay parameter"
                return NodeResult.success(node.next)
            }
        }

        if (ms < 0) {
            context.log("ACTION_DELAY: negative delay ($ms ms); failing")
            context.variables["_last_error"] = true
            context.variables["_last_error_msg"] = "Negative delay: $ms"
            return NodeResult.success(node.next)
        }

        val effective = if (ms > MAX_DELAY_MS) {
            context.log("ACTION_DELAY: requested ${ms}ms capped to ${MAX_DELAY_MS}ms (${
                ms / 60_000
            } min → ${MAX_DELAY_MS / 60_000} min max)")
            context.variables["delay_truncated"] = true
            MAX_DELAY_MS
        } else {
            ms
        }

        if (effective > 0) delay(effective)
        return NodeResult.success(node.next)
    }
}
