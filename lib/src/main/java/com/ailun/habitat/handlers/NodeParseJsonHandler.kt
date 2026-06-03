package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * [ACTION_PARSE_JSON]：解析 JSON 并提取指定路径的值。
 *
 * params：
 * - `json`：JSON 字符串（直接提供）
 * - `json_var`：变量名（从上下文中读取该变量的值作为 JSON）
 *   — 优先使用 `json`，如果 `json` 为空则使用 `json_var`
 * - `path`（可选）：JSONPath 风格路径，如 "data.items[0].name"
 * - `output_var`（可选）：存储结果的变量名
 * - `default_value`（可选）：路径未找到时的默认值
 *
 * 路径语法：
 * - `foo.bar` — 对象字段
 * - `items[0]` — 数组索引
 * - `["a.b"]` — 带特殊字符的 bracket key
 */
class NodeParseJsonHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return node.nextResult()

        // Resolve JSON source: prefer inline `json`, fall back to `json_var` variable lookup.
        val rawJson = params["json"]?.toString()?.trim()
        val jsonString = if (!rawJson.isNullOrEmpty()) {
            try { context.interpolate(rawJson) } catch (_: WorkflowContext.MissingVariableException) { rawJson }
        } else {
            val varName = params["json_var"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            if (varName != null) {
                context.getVariable(varName)?.toString() ?: run {
                    failParse(context, "Variable '$varName' not found or has no string value")
                    return node.nextResult()
                }
            } else {
                failParse(context, "Neither 'json' nor 'json_var' provided")
                return node.nextResult()
            }
        }
        val path = params["path"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val outputVar = params["output_var"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val defaultValue = params["default_value"]?.toString()

        try {
            val jsonValue = JSONTokener(jsonString).nextValue()
            val result: Any? = if (path != null) {
                val (found, value) = navigatePath(jsonValue, path)
                if (!found) {
                    if (defaultValue != null) {
                        defaultValue
                    } else {
                        failParse(context, "JSON path '$path' not found")
                        return node.nextResult()
                    }
                } else {
                    value
                }
            } else {
                jsonValue
            }

            val resultStr = when (result) {
                is JSONObject -> result.toString(2)
                is JSONArray -> result.toString(2)
                JSONObject.NULL -> "null"
                null -> "null"
                else -> result.toString()
            }
            val resultKey = outputVar ?: "parsed_json"
            context.variables[resultKey] = resultStr
            context.variables["parsed_value"] = resultStr
            context.variables["parse_success"] = true
            context.variables["parse_path_found"] = true
            Log.i(TAG, "JSON parsed: ${if (path != null) "path=$path" else "root"}")
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: ${e.message}", e)
            failParse(context, e.message ?: "Unknown parse error")
        }
        return node.nextResult()
    }

    /**
     * Navigate JSON path. Returns (found: Boolean, value: Any?).
     *
     * Supports:
     * - Dot-separated keys: `data.items[0].name`
     * - Bracket keys with quotes: `["a.b"]` for keys containing dots
     * - Array/hash bracket access: `[0]`, `[-1]`, `["key"]`
     */
    private fun navigatePath(root: Any, path: String): Pair<Boolean, Any?> {
        var current: Any? = root
        val segments = tokenizePath(path)

        for (segment in segments) {
            current = when {
                segment.startsWith("[") && segment.endsWith("]") -> {
                    val inner = segment.substring(1, segment.length - 1)
                    // Quoted string key: ["key name"]
                    if (inner.startsWith("\"") && inner.endsWith("\"")) {
                        val key = inner.substring(1, inner.length - 1)
                        (current as? JSONObject)?.opt(key)
                    } else if (inner.startsWith("'") && inner.endsWith("'")) {
                        val key = inner.substring(1, inner.length - 1)
                        (current as? JSONObject)?.opt(key)
                    } else {
                        val idx = inner.toIntOrNull() ?: return false to null
                        val arr = current as? JSONArray ?: return false to null
                        val i = if (idx < 0) arr.length() + idx else idx
                        if (i < 0 || i >= arr.length()) return false to null
                        arr.get(i)
                    }
                }
                else -> {
                    (current as? JSONObject)?.opt(segment)
                        ?: return false to null
                }
            }
        }
        return true to current
    }

    /**
     * Tokenize path "data.items[0].name" or "data[\"a.b\"].key".
     */
    private fun tokenizePath(path: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuoted = false
        var quoteChar = '"'

        var i = 0
        while (i < path.length) {
            val ch = path[i]
            when {
                ch == '"' || ch == '\'' -> {
                    if (inQuoted && ch == quoteChar) {
                        current.append(ch)
                        // Continue appending until ']' — quoted bracket keys
                    } else {
                        inQuoted = !inQuoted
                        quoteChar = ch
                    }
                    current.append(ch)
                }
                inQuoted -> { current.append(ch) }
                ch == '.' -> {
                    if (current.isNotEmpty()) { tokens.add(current.toString()); current.clear() }
                }
                ch == '[' -> {
                    if (current.isNotEmpty()) { tokens.add(current.toString()); current.clear() }
                    current.append(ch)
                }
                ch == ']' -> {
                    current.append(ch)
                    tokens.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    private fun failParse(context: WorkflowContext, error: String) {
        context.variables["parse_success"] = false
        context.variables["parse_error"] = error
        context.variables["parse_path_found"] = false
    }

    companion object {
        private const val TAG = "HabitatParseJson"
    }
}
