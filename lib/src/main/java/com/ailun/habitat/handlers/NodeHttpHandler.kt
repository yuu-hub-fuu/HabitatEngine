package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * [ACTION_HTTP_REQUEST]：发送 HTTP 请求。
 *
 * params：
 * - `url`（必填）：请求 URL
 * - `method`（可选）：GET / POST / PUT / DELETE，默认 GET
 * - `headers`（可选）：JSON 对象字符串或分号分隔的 "key:value" 对
 * - `body`（可选）：请求体
 * - `output_var`（可选）：存储响应体的变量名
 * - `timeout`（可选）：超时时间（毫秒），默认 30000
 */
class NodeHttpHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val params = node.params ?: return node.next

        val rawUrl = params["url"]?.toString()?.trim() ?: run {
            Log.w(TAG, "No URL provided")
            context.variables["http_success"] = false
            context.variables["http_error"] = "No URL provided"
            return node.next
        }

        val url = context.interpolate(rawUrl)
        val method = params["method"]?.toString()?.trim()?.uppercase() ?: "GET"
        val rawHeaders = params["headers"]?.toString()?.trim()
        val body = params["body"]?.toString()?.let { context.interpolate(it) }
        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null }
        val timeout = (params["timeout"] as? Number)?.toInt()?.coerceAtLeast(1000) ?: 30_000

        var connection: HttpURLConnection? = null

        try {
            Log.d(TAG, "$method $url")

            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.instanceFollowRedirects = true

            // Parse and set headers
            val headers = parseHeaders(rawHeaders)
            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }

            // Set default headers if not already set
            if (!headers.containsKey("Accept")) {
                connection.setRequestProperty("Accept", "*/*")
            }

            // Write body for methods that support it
            if (body != null && method in setOf("POST", "PUT", "PATCH")) {
                if (!headers.containsKey("Content-Type")) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.doOutput = true
                connection.outputStream.use { os: OutputStream ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            }

            val statusCode = connection.responseCode
            val responseBody = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                // Try error stream if input stream fails
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            context.variables["http_status_code"] = statusCode
            context.variables["http_response"] = responseBody
            context.variables["http_success"] = statusCode in 200..399

            if (outputVar != null) {
                context.variables[outputVar] = responseBody
            }

            Log.i(TAG, "HTTP $method $url -> $statusCode (${responseBody.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}", e)
            context.variables["http_success"] = false
            context.variables["http_error"] = e.message ?: "Unknown error"
            context.variables["http_status_code"] = 0
            context.variables["http_response"] = ""
        } finally {
            connection?.disconnect()
        }

        return node.next
    }

    /**
     * Parse headers from either JSON object string or semicolon-separated "key:value" pairs.
     */
    private fun parseHeaders(raw: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (raw.isNullOrBlank()) return result

        // Try JSON first
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = json.getString(key)
                }
                return result
            } catch (_: Exception) {
                // Fall through to semicolon parsing
            }
        }

        // Semicolon-separated "key:value" pairs
        val pairs = trimmed.split(";")
        for (pair in pairs) {
            val colonIndex = pair.indexOf(":")
            if (colonIndex > 0) {
                val key = pair.substring(0, colonIndex).trim()
                val value = pair.substring(colonIndex + 1).trim()
                if (key.isNotEmpty()) {
                    result[key] = value
                }
            }
        }

        return result
    }

    companion object {
        private const val TAG = "HabitatHttp"
    }
}
