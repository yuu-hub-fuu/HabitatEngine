package com.ailun.habitat

/**
 * Centralized constants for all runtime status variables written to [WorkflowContext].
 *
 * Handlers and [HabitatExecutor] MUST use these constants instead of raw string keys
 * to prevent typos and ensure consistent semantics across the engine.
 */
object RuntimeVars {
    // ── Status ──
    const val LAST_SUCCESS = "_last_success"
    const val LAST_ERROR = "_last_error"
    const val LAST_ERROR_MSG = "_last_error_msg"

    // ── Risk ──
    const val LAST_RISK_LEVEL = "_last_risk_level"
    const val LAST_RISK_CAPABILITY = "_last_risk_capability"
    const val LAST_RISK_BLOCKED = "_last_risk_blocked"
    const val LAST_RISK_REASON = "_last_risk_reason"

    // ── Confirmation ──
    const val CONFIRM_REQUIRED = "_confirm_required"
    const val CONFIRM_MESSAGE = "_confirm_message"

    // ── Workflow-level ──
    const val WORKFLOW_SUCCESS = "_workflow_success"

    // ── Shell ──
    const val SHELL_OUTPUT = "shell_output"
    const val SHELL_SUCCESS = "shell_success"
    const val SHELL_EXIT_CODE = "shell_exit_code"
}
