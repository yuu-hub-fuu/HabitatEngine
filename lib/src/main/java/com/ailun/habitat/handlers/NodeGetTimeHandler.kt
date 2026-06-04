package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * [ACTION_GET_TIME]：获取当前时间。
 */
class NodeGetTimeHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val format = node.params?.get("format")?.toString() ?: "yyyy-MM-dd HH:mm:ss"
        val useTimestamp = node.params?.get("timestamp")?.toString()?.equals("true", true) == true
        val outputVar = node.params?.get("output_var")?.toString()?.ifEmpty { null } ?: "current_time"

        val now = Date()
        val formatted = try {
            SimpleDateFormat(format, Locale.getDefault()).format(now)
        } catch (e: Exception) {
            now.toString()
        }

        val cal = Calendar.getInstance()
        context.log("GetTime format=$format → $formatted")
        return NodeResult.success(node.next, mapOf(
            outputVar to formatted,
            "current_time" to formatted,
            "current_timestamp" to now.time,
            "current_year" to cal.get(Calendar.YEAR),
            "current_month" to cal.get(Calendar.MONTH) + 1,
            "current_day" to cal.get(Calendar.DAY_OF_MONTH),
            "current_hour" to cal.get(Calendar.HOUR_OF_DAY),
            "current_minute" to cal.get(Calendar.MINUTE),
            "current_second" to cal.get(Calendar.SECOND),
            "current_weekday" to cal.get(Calendar.DAY_OF_WEEK) - 1,
            "time_success" to true,
        ))
    }
}
