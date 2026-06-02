package com.ailun.habitat.confirmation

import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.ConfirmationRequest
import com.ailun.habitat.api.IConfirmationProvider
import com.ailun.habitat.capability.RiskEngine
import java.util.UUID

/**
 * Manages the lifecycle of one-time confirmation tokens.
 *
 * Tokens are generated per-node-per-execution. Once approved and consumed,
 * the token cannot be reused. Tokens expire after the execution completes.
 */
class ConfirmationManager(
    private val provider: IConfirmationProvider?,
    private val riskEngine: RiskEngine = RiskEngine(),
) {
    /** Approved tokens that are valid for this execution. */
    private val approvedTokens = mutableSetOf<String>()

    /** Clear all approved tokens (called when execution ends). */
    fun reset() {
        approvedTokens.clear()
    }

    /**
     * Generate a new one-time token.
     */
    fun generateToken(): String = UUID.randomUUID().toString()

    /**
     * Check if a token has been approved (validates and consumes it).
     * Returns true if the token is in the approved set.
     * The token is consumed (removed) on successful validation.
     */
    fun validateAndConsume(token: String?): Boolean {
        if (token == null) return false
        return approvedTokens.remove(token)
    }

    /**
     * Ensure the user has confirmed execution of this node.
     *
     * - If no provider is registered, confirmation is skipped (test/dev mode).
     * - If the node's risk level is below CONFIRMATION_THRESHOLD, skip confirmation.
     * - If the node already has an approved token, skip confirmation.
     * - Otherwise, request user confirmation and store the approved token.
     *
     * @param node The node about to be executed.
     * @param interpolatedParams Pre-interpolated parameters for display.
     * @return true if execution is allowed, false if denied.
     */
    suspend fun ensureConfirmed(
        node: WorkflowNode,
        interpolatedParams: Map<String, String> = emptyMap(),
    ): Boolean {
        // No provider → skip confirmation (dev/test mode)
        if (provider == null) return true

        // Check if the node has a pre-approved token from compiler
        val preToken = node.params?.get("_confirm_token")?.toString()
        if (preToken != null && validateAndConsume(preToken)) {
            return true
        }

        // Check risk level
        val assessment = riskEngine.assessNode(node)
        if (!assessment.requiresConfirmation) return true

        // Build request
        val request = ConfirmationRequest(
            requestId = UUID.randomUUID().toString(),
            nodeId = node.id ?: "unknown",
            nodeType = node.type ?: "unknown",
            description = buildDescription(node, interpolatedParams),
            riskLevel = assessment.riskLevel,
            details = interpolatedParams + mapOf(
                "node_type" to (node.type ?: "unknown"),
                "risk_reasons" to assessment.riskReasons.joinToString("; "),
            ),
            oneTimeToken = generateToken(),
        )

        val response = provider.requestConfirmation(request)
        return if (response.approved && response.oneTimeToken == request.oneTimeToken) {
            approvedTokens.add(response.oneTimeToken)
            true
        } else {
            false
        }
    }

    private fun buildDescription(
        node: WorkflowNode,
        interpolatedParams: Map<String, String>,
    ): String {
        val type = node.type ?: "unknown"
        val label = node.label ?: node.description ?: type

        return when (type) {
            "ACTION_SHELL" -> {
                val cmd = interpolatedParams["command"] ?: node.params?.get("command")?.toString() ?: ""
                if (cmd.length > 80) "执行 Shell: ${cmd.take(80)}..." else "执行 Shell: $cmd"
            }
            "ACTION_FILE_OPERATION" -> {
                val action = interpolatedParams["action"] ?: node.params?.get("action")?.toString() ?: ""
                val path = interpolatedParams["path"] ?: node.params?.get("path")?.toString() ?: ""
                "文件操作 ($action): $path"
            }
            "ACTION_CALL_PHONE" -> {
                val number = interpolatedParams["phone_number"] ?: node.params?.get("phone_number")?.toString() ?: ""
                "拨打电话: $number"
            }
            "ACTION_SHARE" -> {
                val text = interpolatedParams["text"] ?: node.params?.get("text")?.toString() ?: ""
                if (text.length > 50) "分享内容: ${text.take(50)}..." else "分享内容: $text"
            }
            "ACTION_HTTP_REQUEST" -> {
                val method = interpolatedParams["method"] ?: node.params?.get("method")?.toString() ?: "GET"
                val url = interpolatedParams["url"] ?: node.params?.get("url")?.toString() ?: ""
                "HTTP $method: $url"
            }
            "ACTION_READ_SMS" -> "读取短信内容"
            "ACTION_SEND_NOTIFICATION" -> "发送系统通知"
            else -> "执行节点: $label"
        }
    }
}
