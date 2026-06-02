package com.ailun.habitat.capability

/**
 * Granular capabilities that a workflow or node may require.
 * Each capability maps to a set of ACTION_* types and has a default risk level.
 *
 * These are used by:
 * - RiskEngine: to assess per-node and per-graph risk
 * - PlanIR: to declare what a plan requires
 * - ConfirmationManager: to decide which actions need user approval
 */
enum class Capability(
    val displayName: String,
    val defaultRisk: RiskLevel,
) {
    // ── Screen interaction (mostly LOW risk) ──
    SCREEN_READ("Read screen content", RiskLevel.LOW),
    SCREEN_CLICK("Click/tap on screen", RiskLevel.LOW),
    SCREEN_SWIPE("Swipe gesture", RiskLevel.LOW),
    SCREEN_INPUT("Input text into field", RiskLevel.LOW),
    SCREEN_LONG_PRESS("Long press", RiskLevel.LOW),
    SCREEN_SCREENSHOT("Capture screenshot", RiskLevel.LOW),

    // ── Privacy-sensitive ──
    SMS_READ("Read SMS messages", RiskLevel.HIGH),
    CLIPBOARD_READ("Read clipboard content", RiskLevel.MEDIUM),
    CLIPBOARD_WRITE("Write to clipboard", RiskLevel.LOW),

    // ── File operations ──
    FILE_READ("Read files", RiskLevel.LOW),
    FILE_WRITE("Write/create files", RiskLevel.MEDIUM),
    FILE_DELETE("Delete files or directories", RiskLevel.HIGH),

    // ── Shell / system ──
    SHELL_EXEC("Execute shell commands", RiskLevel.HIGH),
    ROOT_EXEC("Execute root commands", RiskLevel.CRITICAL),

    // ── Network ──
    NETWORK_GET("HTTP GET requests", RiskLevel.LOW),
    NETWORK_POST("HTTP POST/PUT — may exfiltrate data", RiskLevel.MEDIUM),

    // ── Communication ──
    PHONE_CALL("Place phone calls", RiskLevel.HIGH),
    SMS_SEND("Send SMS messages", RiskLevel.HIGH),
    SHARE("Share content to other apps", RiskLevel.MEDIUM),

    // ── App management ──
    APP_LAUNCH("Launch other apps", RiskLevel.LOW),
    APP_FORCE_STOP("Force-stop other apps", RiskLevel.MEDIUM),

    // ── System settings ──
    WIFI_CONTROL("Toggle WiFi", RiskLevel.LOW),
    BLUETOOTH_CONTROL("Toggle Bluetooth", RiskLevel.LOW),
    VOLUME_CONTROL("Adjust volume", RiskLevel.LOW),
    BRIGHTNESS_CONTROL("Adjust brightness", RiskLevel.LOW),
    FLASHLIGHT_CONTROL("Toggle flashlight", RiskLevel.LOW),
    SCREEN_WAKE_CONTROL("Wake/sleep screen", RiskLevel.LOW),

    // ── UI / Notifications ──
    NOTIFICATION_SEND("Post notifications", RiskLevel.LOW),
    OVERLAY_DISPLAY("Show overlay windows", RiskLevel.LOW),

    // ── AI ──
    AI_INFERENCE("Invoke on-device LLM", RiskLevel.LOW),

    // ── Connectivity ──
    NETWORK_STATUS("Query network status", RiskLevel.LOW),
    ;

    companion object {
        /** All capabilities ordered by risk descending. */
        val byRiskDescending: List<Capability> = values().sortedByDescending { it.defaultRisk.score }
    }
}
