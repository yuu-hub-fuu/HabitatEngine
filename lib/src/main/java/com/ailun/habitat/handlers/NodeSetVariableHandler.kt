package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_SET_VARIABLE]：动态设置上下文变量。
 *
 * params：
 * - `name` 或 `key`（必填）：变量名。支持 `.` 分隔的嵌套路径：
 *   `"user.name"` → 设置 map["user"]["name"]，自动创建中间 map
 * - `value`（任意类型）：要设置的值
 * - `type`（可选）：auto / string / int / bool / float / json / array
 *   - "auto" (默认): 保持原始类型
 *   - "string": 强制转字符串
 *   - "int": 强制转整数
 *   - "bool": 强制转布尔
 *   - "float": 强制转浮点
 *   - "json": 解析 value 为 JSON 对象/数组
 *   - "array": 将逗号分隔的字符串拆分为 List<String>
 *
 * 示例：
 * ```json
 * { "type": "ACTION_SET_VARIABLE", "params": { "key": "user.age", "value": 30, "type": "int" } }
 * { "type": "ACTION_SET_VARIABLE", "params": { "key": "tags", "value": "a,b,c", "type": "array" } }
 * { "type": "ACTION_SET_VARIABLE", "params": { "key": "config", "value": "{\"debug\":true}", "type": "json" } }
 * ```
 */
class NodeSetVariableHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val key = (node.params?.get("name")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: node.params?.get("key")?.toString()?.trim())
            ?: return NodeResult.failure(node.next, "Missing 'key'/'name' parameter")

        val rawValue = node.params?.get("value")
        val typeStr = node.params?.get("type")?.toString()?.trim()?.lowercase() ?: "auto"

        val value = when (typeStr) {
            "string" -> rawValue?.toString()
            "int" -> (rawValue as? Number)?.toInt() ?: rawValue?.toString()?.toIntOrNull()
                ?: return NodeResult.failure(node.next, "Cannot convert '$rawValue' to int")
            "bool" -> when {
                rawValue is Boolean -> rawValue
                rawValue.toString().equals("true", ignoreCase = true) -> true
                rawValue.toString().equals("false", ignoreCase = true) -> false
                else -> return NodeResult.failure(node.next, "Cannot convert '$rawValue' to bool")
            }
            "float" -> (rawValue as? Number)?.toDouble() ?: rawValue?.toString()?.toDoubleOrNull()
                ?: return NodeResult.failure(node.next, "Cannot convert '$rawValue' to float")
            "json" -> parseJson(rawValue?.toString())
                ?: return NodeResult.failure(node.next, "Invalid JSON: $rawValue")
            "array" -> rawValue?.toString()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: return NodeResult.failure(node.next, "Cannot parse array from '$rawValue'")
            else -> rawValue // auto: keep original type (Number, Boolean, String, etc.)
        }

        // Support nested path: "user.profile.name" → context.variables["user"]["profile"]["name"]
        if (key.contains('.')) {
            setNested(context, key, value)
        } else {
            context.putVariable(key, value)
        }

        context.log("SetVariable $key = $value (type=$typeStr)")
        return NodeResult.success(node.next, mapOf(
            "setvar_success" to true, key to value,
        ))
    }

    @Suppress("UNCHECKED_CAST")
    private fun setNested(context: WorkflowContext, dottedPath: String, value: Any?) {
        val parts = dottedPath.split('.')
        val rootKey = parts.first()
        var current = context.getVariable(rootKey) as? MutableMap<String, Any?>
        if (current == null) {
            current = mutableMapOf()
            context.putVariable(rootKey, current)
        }
        for (i in 1 until parts.size - 1) {
            val part = parts[i]
            val next = current!![part] as? MutableMap<String, Any?>
            if (next == null) {
                val newMap = mutableMapOf<String, Any?>()
                current!![part] = newMap
                current = newMap
            } else {
                current = next
            }
        }
        current!![parts.last()] = value
    }

    private fun parseJson(raw: String?): Any? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return try {
            when {
                trimmed.startsWith("[") -> org.json.JSONArray(trimmed)
                trimmed.startsWith("{") -> org.json.JSONObject(trimmed)
                trimmed == "null" -> null
                trimmed == "true" -> true
                trimmed == "false" -> false
                trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
                else -> trimmed
            }
        } catch (_: Exception) { null }
    }
}
