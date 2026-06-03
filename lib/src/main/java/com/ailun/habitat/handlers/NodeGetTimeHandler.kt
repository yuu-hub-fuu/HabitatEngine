package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [ACTION_GET_TIME]：获取当前时间。
 * params：
 * - `format`（可选）：时间格式，默认 "yyyy-MM-dd HH:mm:ss"
 * - `output_var`（可选）：输出变量名，默认 "current_time"
 * - `timestamp`（可选）：true 返回 Unix 毫秒时间戳，默认 false
 * 输出：current_time、current_year、current_month、current_day、current_hour、current_minute、current_second
 */
class NodeGetTimeHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val format = node.params?.get("format")?.toString() ?: "yyyy-MM-dd HH:mm:ss"
        val useTimestamp = node.params?.get("timestamp")?.toString()?.equals("true", true) == true
        val outputVar = node.params?.get("output_var")?.toString()?.ifEmpty { null } ?: "current_time"

        val now = Date()
        try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            context.variables[outputVar] = sdf.format(now)
        } catch (e: Exception) {
            context.variables[outputVar] = now.toString()
        }
        context.log("GetTime format=$format → ${context.variables[outputVar]}")
        context.variables["current_timestamp"] = now.time
        context.variables["current_time"] = context.variables[outputVar]

        val cal = java.util.Calendar.getInstance()
        context.variables["current_year"] = cal.get(java.util.Calendar.YEAR)
        context.variables["current_month"] = cal.get(java.util.Calendar.MONTH) + 1
        context.variables["current_day"] = cal.get(java.util.Calendar.DAY_OF_MONTH)
        context.variables["current_hour"] = cal.get(java.util.Calendar.HOUR_OF_DAY)
        context.variables["current_minute"] = cal.get(java.util.Calendar.MINUTE)
        context.variables["current_second"] = cal.get(java.util.Calendar.SECOND)
        context.variables["current_weekday"] = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
        context.variables["time_success"] = true
        return node.nextResult()
    }
}
