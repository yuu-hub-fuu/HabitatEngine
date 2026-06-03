package com.ailun.habitat.handlers

import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
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
 *
 * **Security:**
 * - Only https:// URLs are allowed (plus http:// to localhost/private IPs).
 * - file://, content://, etc. are rejected.
 * - Response body is capped at [MAX_RESPONSE_BYTES].
 * - Redirects are followed but checked against the same URL policy.
 */
class NodeHttpHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return node.nextResult()

        val rawUrl = params["url"]?.toString()?.trim() ?: run {
            Log.w(TAG, "No URL provided")
            fail(context, "No URL provided")
            return node.nextResult()
        }

        val url = context.interpolate(rawUrl)
        val method = params["method"]?.toString()?.trim()?.uppercase() ?: "GET"
        val rawHeaders = params["headers"]?.toString()?.trim()
        val body = params["body"]?.toString()?.let { context.interpolate(it) }
        val outputVar = params["output_var"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val timeout = (params["timeout"] as? Number)?.toInt()?.coerceAtLeast(1_000) ?: 30_000

        // ── URL validation ──
        val uri: URI
        try {
            uri = URI(url)
        } catch (e: Exception) {
            fail(context, "Invalid URL: ${e.message}")
            return node.nextResult()
        }
        val blocked = validateUrl(uri)
        if (blocked != null) {
            Log.w(TAG, "URL blocked: $url — $blocked")
            fail(context, blocked)
            return node.nextResult()
        }

        var connection: HttpURLConnection? = null

        try {
            Log.d(TAG, "$method $url")

            connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeout
                readTimeout = timeout
                instanceFollowRedirects = false // We check redirect targets ourselves
            }

            // Parse headers with variable interpolation
            val headers = parseHeaders(rawHeaders, context)
            for ((key, value) in headers) {
                connection.setRequestProperty(key, value)
            }
            if (!headers.containsKey("Accept")) {
                connection.setRequestProperty("Accept", "*/*")
            }

            // Write body
            if (body != null && method in BODY_METHODS) {
                if (!headers.containsKey("Content-Type")) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.doOutput = true
                connection.outputStream.use { os: OutputStream ->
                    os.write(body.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            }

            // Handle redirects manually to validate target
            val statusCode = connection.responseCode
            if (statusCode in 301..308) {
                val redirectUrl = connection.getHeaderField("Location")
                connection.disconnect(); connection = null
                if (redirectUrl == null) {
                    fail(context, "Redirect without Location header")
                    return node.nextResult()
                }
                // Resolve relative redirects against the original URI
                val redirectUri = uri.resolve(redirectUrl)
                val redirectBlocked = validateUrl(redirectUri)
                if (redirectBlocked != null) {
                    fail(context, "Redirect blocked: $redirectBlocked")
                    return node.nextResult()
                }
                // Follow the redirect with a new connection (single hop only).
                return followRedirect(redirectUri, method, headers, body, timeout, outputVar, context, node.next)
            }

            // Read response body — loop-read until EOF or MAX_RESPONSE_BYTES
            val responseBody = readResponseBody(connection)

            context.variables["http_status_code"] = statusCode
            context.variables["http_response"] = responseBody
            context.variables["http_success"] = statusCode in 200..399
            context.variables["http_response_size"] = responseBody.length
            outputVar?.let { context.variables[it] = responseBody }
            Log.i(TAG, "HTTP $method $url -> $statusCode (${responseBody.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}", e)
            fail(context, e.message ?: "Unknown error")
            context.variables["http_status_code"] = 0
            context.variables["http_response"] = ""
        } finally {
            connection?.disconnect()
        }

        return node.nextResult()
    }

    private fun validateUrl(uri: URI): NodeResult {
        val scheme = uri.scheme?.lowercase()

        // Reject non-http schemes.
        if (scheme !in ALLOWED_SCHEMES) return "Scheme '$scheme' is not allowed"

        // Reject file://, content://, etc. (non-http cases caught above, but be explicit).
        if (scheme == null) return "Missing URL scheme"

        val host = uri.host ?: return "Missing host"

        // http:// is allowed only for localhost/private IPs.
        if (scheme == "http") {
            if (!isLocalOrPrivateHost(host)) {
                return "HTTP is only allowed on localhost/private IPs; use HTTPS"
            }
        }

        return null
    }

    private fun isLocalOrPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("172.")) {
            try {
                val addr = InetAddress.getByName(host)
                return addr.isSiteLocalAddress || addr.isLoopbackAddress
            } catch (_: Exception) { return false }
        }
        return false
    }

    private fun followRedirect(
        uri: URI, method: String, headers: Map<String, String>,
        body: String?, timeout: Int, outputVar: String?,
        context: WorkflowContext, nextId: String?,
    ): NodeResult {
        var connection: HttpURLConnection? = null
        try {
            connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeout
                readTimeout = timeout
                instanceFollowRedirects = false
            }
            for ((k, v) in headers) connection.setRequestProperty(k, v)
            if (body != null && method in BODY_METHODS) {
                connection.doOutput = true
                connection.outputStream.use { os: OutputStream -> os.write(body.toByteArray(Charsets.UTF_8)); os.flush() }
            }
            val code = connection.responseCode
            val resp = readResponseBody(connection)
            context.variables["http_status_code"] = code
            context.variables["http_response"] = resp
            context.variables["http_success"] = code in 200..399
            outputVar?.let { context.variables[it] = resp }
            Log.i(TAG, "HTTP redirect $method ${uri} -> $code")
        } catch (e: Exception) {
            fail(context, e.message ?: "Redirect failed")
        } finally { connection?.disconnect() }
        return nextId
    }

    /** Loop-read response body until EOF or [MAX_RESPONSE_BYTES]. */
    private fun readResponseBody(connection: HttpURLConnection): String {
        return try {
            connection.inputStream.bufferedReader().use { reader ->
                val sb = StringBuilder()
                var total = 0
                val buf = CharArray(4096)
                var n: Int
                while (reader.read(buf).also { n = it } != -1 && total < MAX_RESPONSE_BYTES) {
                    val toWrite = n.coerceAtMost(MAX_RESPONSE_BYTES - total)
                    sb.append(buf, 0, toWrite)
                    total += toWrite
                }
                if (total >= MAX_RESPONSE_BYTES) sb.append("...[truncated]")
                sb.toString()
            }
        } catch (e: Exception) {
            connection.errorStream?.bufferedReader()?.use { reader ->
                val sb = StringBuilder()
                var total = 0
                val buf = CharArray(4096)
                var n: Int
                while (reader.read(buf).also { n = it } != -1 && total < MAX_RESPONSE_BYTES) {
                    val toWrite = n.coerceAtMost(MAX_RESPONSE_BYTES - total)
                    sb.append(buf, 0, toWrite)
                    total += toWrite
                }
                sb.toString()
            } ?: ""
        }
    }

    /**
     * Parse headers from JSON or semicolon-separated "key:value" pairs,
     * with variable interpolation on header values.
     */
    private fun parseHeaders(raw: String?, context: WorkflowContext): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (raw.isNullOrBlank()) return result

        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val rawValue = json.getString(key)
                    result[key] = context.interpolate(rawValue)
                }
                return result
            } catch (_: Exception) { /* fall through */ }
        }

        val pairs = trimmed.split(";")
        for (pair in pairs) {
            val colonIndex = pair.indexOf(":")
            if (colonIndex > 0) {
                val key = pair.substring(0, colonIndex).trim()
                val rawValue = pair.substring(colonIndex + 1).trim()
                if (key.isNotEmpty()) {
                    result[key] = context.interpolate(rawValue)
                }
            }
        }
        return result
    }

    private fun fail(context: WorkflowContext, error: String) {
        context.variables["http_success"] = false
        context.variables["http_error"] = error
        context.variables["http_status_code"] = 0
        context.variables["http_response"] = ""
    }

    companion object {
        private const val TAG = "HabitatHttp"
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private val ALLOWED_SCHEMES = setOf("https", "http")
        private val BODY_METHODS = setOf("POST", "PUT", "PATCH")
    }
}
