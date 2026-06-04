package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * [ACTION_HTTP_REQUEST] — 执行 HTTP 请求。
 *
 * params:
 * - `url` (必填): 请求 URL
 * - `method` (可选): GET / POST / PUT / PATCH / DELETE，默认 GET
 * - `headers` (可选): JSON object of headers
 * - `body` (可选): request body (string or JSON)
 * - `content_type` (可选): default "application/json; charset=utf-8"
 * - `timeout_ms` (可选): default 30_000
 * - `output_var` (可选): 存储响应体的变量名，默认 http_response
 *
 * 输出变量:
 * - `http_success` (Boolean)
 * - `http_status_code` (Int)
 * - `http_response` (String) — response body
 * - `http_error` (String) — 错误信息
 */
class NodeHttpHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val rawUrl = params["url"]?.toString()?.trim()
            ?: return NodeResult.failure(node.next, "Missing 'url' parameter",
                mapOf("http_success" to false))

        val url = context.interpolate(rawUrl)
        val httpMethod = (params["method"]?.toString()?.trim()?.uppercase() ?: "GET")
            .takeIf { it in ALLOWED_METHODS }
            ?: return NodeResult.failure(node.next, "Invalid HTTP method: ${params["method"]}",
                mapOf("http_success" to false))

        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null } ?: "http_response"
        val timeoutMs = (params["timeout_ms"] as? Number)?.toLong()?.coerceIn(1_000, 120_000) ?: 30_000L
        val contentType = params["content_type"]?.toString()?.trim() ?: "application/json; charset=utf-8"
        val body = params["body"]?.toString()?.let { context.interpolate(it) }

        // Parse optional headers from JSON string
        val headers = parseHeaders(params["headers"])

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = httpMethod
                connection.connectTimeout = timeoutMs.toInt()
                connection.readTimeout = timeoutMs.toInt()
                connection.setRequestProperty("Content-Type", contentType)
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

                // Write body for POST/PUT/PATCH
                if (body != null && httpMethod in BODY_METHODS) {
                    connection.doOutput = true
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
                }

                val statusCode = connection.responseCode
                val responseBody = try {
                    BufferedReader(InputStreamReader(
                        if (statusCode in 200..299) connection.inputStream
                        else connection.errorStream ?: connection.inputStream,
                        Charsets.UTF_8
                    )).use { it.readText() }
                } catch (_: Exception) { "" }

                context.log("HTTP $httpMethod $url → $statusCode (${responseBody.length} bytes)")
                connection.disconnect()
                connection = null

                val success = statusCode in 200..299
                return@withContext if (success) {
                    NodeResult.success(node.next, mapOf(
                        outputVar to responseBody, "http_response" to responseBody,
                        "http_success" to true, "http_status_code" to statusCode,
                    ))
                } else {
                    NodeResult.failure(
                        node.next, "HTTP $statusCode",
                        mapOf(outputVar to responseBody, "http_response" to responseBody,
                            "http_success" to false, "http_status_code" to statusCode,
                            "http_error" to "HTTP $statusCode"),
                    )
                }
            } catch (e: java.net.SocketTimeoutException) {
                context.log("HTTP $httpMethod $url → TIMEOUT")
                NodeResult.failure(node.next, "Request timed out after ${timeoutMs}ms",
                    mapOf("http_success" to false, "http_status_code" to 0,
                        "http_error" to "timeout"))
            } catch (e: java.net.UnknownHostException) {
                context.log("HTTP $httpMethod $url → UNKNOWN HOST")
                NodeResult.failure(node.next, "Unknown host: ${e.message}",
                    mapOf("http_success" to false, "http_status_code" to 0,
                        "http_error" to "unknown_host"))
            } catch (e: Exception) {
                context.log("HTTP $httpMethod $url → ERROR: ${e.message}")
                NodeResult.failure(node.next, "HTTP error: ${e.message}",
                    mapOf("http_success" to false, "http_status_code" to 0,
                        "http_error" to (e.message ?: "Unknown")))
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseHeaders(raw: Any?): Map<String, String> {
        if (raw == null) return emptyMap()
        return when (raw) {
            is Map<*, *> -> raw.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
            is String -> {
                try {
                    // Try JSON: {"Authorization":"Bearer xxx","X-Custom":"v"}
                    org.json.JSONObject(raw).let { jo ->
                        jo.keys().asSequence().associateWith { jo.getString(it) }
                    }
                } catch (_: Exception) {
                    // Try key:value per line
                    raw.lines().mapNotNull { line ->
                        val colon = line.indexOf(':')
                        if (colon > 0) {
                            line.substring(0, colon).trim() to line.substring(colon + 1).trim()
                        } else null
                    }.toMap()
                }
            }
            else -> emptyMap()
        }
    }

    companion object {
        private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
        private val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    }
}
