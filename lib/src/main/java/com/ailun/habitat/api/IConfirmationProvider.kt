package com.ailun.habitat.api

import com.ailun.habitat.capability.RiskLevel

/**
 * Confirmation request sent to the user before executing a dangerous action.
 */
data class ConfirmationRequest(
    /** Unique request ID (UUID). */
    val requestId: String,

    /** The node ID that triggered this confirmation. */
    val nodeId: String,

    /** The node type (ACTION_SHELL, ACTION_CALL_PHONE, etc.). */
    val nodeType: String,

    /** Plain-language description of what will happen. */
    val description: String,

    /** The risk level of the action. */
    val riskLevel: RiskLevel,

    /** Additional context for the user (e.g., the interpolated command). */
    val details: Map<String, String> = emptyMap(),

    /** Pre-generated one-time token the user must approve. */
    val oneTimeToken: String,
)

/**
 * User's response to a confirmation request.
 */
data class ConfirmationResponse(
    /** Whether the user approved the action. */
    val approved: Boolean,

    /** The token that was approved (must match the request). */
    val oneTimeToken: String,

    /** Optional note from the user. */
    val userNote: String? = null,
)

/**
 * Platform-provided confirmation UI.
 *
 * The app module implements this via [com.ailun.habitat.app.confirmation.ComposeConfirmationProvider]
 * which shows a Compose dialog with action details and Approve/Deny buttons.
 */
interface IConfirmationProvider {
    /**
     * Request user confirmation for a potentially dangerous action.
     * Suspends until the user responds or the timeout is reached.
     *
     * @param request Details of what action needs confirmation.
     * @param timeoutMs Maximum time to wait for a response (default 30s).
     * @return The user's response, or a denied response on timeout.
     */
    suspend fun requestConfirmation(
        request: ConfirmationRequest,
        timeoutMs: Long = 30_000L,
    ): ConfirmationResponse
}
