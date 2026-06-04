package com.ailun.habitat.capability

/**
 * Static mapping from ACTION_* node type constants to required capabilities and risk levels.
 *
 * This is the single source of truth for security classification of every handler.
 * When a new handler is registered, its type should be added here.
 */
object CapabilityMapping {

    /**
     * Returns the set of capabilities required by a given node type.
     * Returns empty set for unknown types (custom/external handlers).
     */
    fun capabilitiesForNodeType(type: String): Set<Capability> {
        return typeToCapabilities[type] ?: emptySet()
    }

    /**
     * Returns the risk level for a given node type.
     * Returns MEDIUM for unknown types (conservative default).
     */
    fun riskLevelForNodeType(type: String): RiskLevel {
        return typeToRisk[type] ?: RiskLevel.MEDIUM
    }

    /**
     * Returns true if this node type requires user confirmation before execution.
     */
    fun requiresConfirmation(type: String): Boolean {
        val risk = riskLevelForNodeType(type)
        return risk.score >= RiskLevel.CONFIRMATION_THRESHOLD_SCORE
    }

    // ──────── Type → Capabilities ────────

    private val typeToCapabilities: Map<String, Set<Capability>> = mapOf(
        // Logic control — no capabilities needed
        "CONDITION_SWITCH" to emptySet(),
        "CONDITION_ADVANCED_SWITCH" to emptySet(),
        "ACTION_DELAY" to emptySet(),
        "ACTION_LOOP" to emptySet(),
        "ACTION_TRY_CATCH" to emptySet(),
        "ACTION_LOG" to emptySet(),

        // Variables & data
        "ACTION_SET_VARIABLE" to emptySet(),
        "ACTION_TEXT_OPERATION" to emptySet(),
        "ACTION_MATH" to emptySet(),
        "ACTION_CLIPBOARD" to setOf(Capability.CLIPBOARD_READ, Capability.CLIPBOARD_WRITE),
        "ACTION_PARSE_JSON" to emptySet(),
        "ACTION_BASE64" to emptySet(),
        "ACTION_FILE_OPERATION" to setOf(Capability.FILE_READ, Capability.FILE_WRITE, Capability.FILE_DELETE),

        // Interaction
        "ACTION_CLICK" to setOf(Capability.SCREEN_CLICK),
        "ACTION_SWIPE" to setOf(Capability.SCREEN_SWIPE),
        "ACTION_LONG_PRESS" to setOf(Capability.SCREEN_LONG_PRESS),
        "ACTION_INPUT_TEXT" to setOf(Capability.SCREEN_INPUT),
        "ACTION_GLOBAL_KEY" to setOf(Capability.SCREEN_CLICK),
        "ACTION_FIND_ELEMENT" to setOf(Capability.SCREEN_READ),

        // System control
        "ACTION_SHELL" to setOf(Capability.SHELL_EXEC, Capability.ROOT_EXEC),
        "ACTION_LAUNCH_APP" to setOf(Capability.APP_LAUNCH),
        "ACTION_FORCE_STOP_APP" to setOf(Capability.APP_FORCE_STOP),
        "ACTION_SCREEN_WAKE" to setOf(Capability.SCREEN_WAKE_CONTROL),
        "ACTION_WIFI" to setOf(Capability.WIFI_CONTROL),
        "ACTION_BLUETOOTH" to setOf(Capability.BLUETOOTH_CONTROL),
        "ACTION_VOLUME" to setOf(Capability.VOLUME_CONTROL),
        "ACTION_BRIGHTNESS" to setOf(Capability.BRIGHTNESS_CONTROL),
        "ACTION_CALL_PHONE" to setOf(Capability.PHONE_CALL),
        "ACTION_SHARE" to setOf(Capability.SHARE),

        // Media
        "ACTION_SCREENSHOT" to setOf(Capability.SCREEN_SCREENSHOT),
        "ACTION_TEXT_TO_SPEECH" to emptySet(),

        // Network
        "ACTION_HTTP_REQUEST" to setOf(Capability.NETWORK_GET, Capability.NETWORK_POST),
        "ACTION_NETWORK_STATUS" to setOf(Capability.NETWORK_STATUS),

        // Tools
        "ACTION_GET_TIME" to emptySet(),
        "ACTION_RANDOM" to emptySet(),

        // Perception
        "ACTION_READ_SCREEN" to setOf(Capability.SCREEN_READ),
        "ACTION_READ_SMS" to setOf(Capability.SMS_READ),
        "ACTION_GET_APP_INFO" to setOf(Capability.SCREEN_READ),
        "ACTION_APP_SEARCH" to emptySet(),

        // UI
        "ACTION_TOAST" to emptySet(),
        "ACTION_VIBRATE" to emptySet(),

        // AI
        "ACTION_AI_CHAT" to setOf(Capability.AI_INFERENCE),

        // App-specific
        "ACTION_SEND_NOTIFICATION" to setOf(Capability.NOTIFICATION_SEND),
        "ACTION_DYNAMIC_ISLAND" to setOf(Capability.OVERLAY_DISPLAY),

        // New types (Phase 1-3)
        "ACTION_CONFIRM" to emptySet(),
    )

    // ──────── Type → RiskLevel ────────

    private val typeToRisk: Map<String, RiskLevel> = mapOf(
        "CONDITION_SWITCH" to RiskLevel.LOW,
        "CONDITION_ADVANCED_SWITCH" to RiskLevel.LOW,
        "ACTION_DELAY" to RiskLevel.LOW,
        "ACTION_LOOP" to RiskLevel.LOW,
        "ACTION_TRY_CATCH" to RiskLevel.LOW,
        "ACTION_LOG" to RiskLevel.LOW,

        "ACTION_SET_VARIABLE" to RiskLevel.LOW,
        "ACTION_TEXT_OPERATION" to RiskLevel.LOW,
        "ACTION_MATH" to RiskLevel.LOW,
        "ACTION_CLIPBOARD" to RiskLevel.MEDIUM,
        "ACTION_PARSE_JSON" to RiskLevel.LOW,
        "ACTION_BASE64" to RiskLevel.LOW,
        "ACTION_FILE_OPERATION" to RiskLevel.HIGH,  // Can delete files

        "ACTION_CLICK" to RiskLevel.LOW,
        "ACTION_SWIPE" to RiskLevel.LOW,
        "ACTION_LONG_PRESS" to RiskLevel.LOW,
        "ACTION_INPUT_TEXT" to RiskLevel.LOW,
        "ACTION_GLOBAL_KEY" to RiskLevel.LOW,
        "ACTION_FIND_ELEMENT" to RiskLevel.LOW,

        "ACTION_SHELL" to RiskLevel.CRITICAL,       // Arbitrary command execution
        "ACTION_LAUNCH_APP" to RiskLevel.LOW,
        "ACTION_FORCE_STOP_APP" to RiskLevel.MEDIUM,
        "ACTION_SCREEN_WAKE" to RiskLevel.LOW,
        "ACTION_WIFI" to RiskLevel.LOW,
        "ACTION_BLUETOOTH" to RiskLevel.LOW,
        "ACTION_VOLUME" to RiskLevel.LOW,
        "ACTION_BRIGHTNESS" to RiskLevel.LOW,
        "ACTION_CALL_PHONE" to RiskLevel.HIGH,
        "ACTION_SHARE" to RiskLevel.MEDIUM,

        "ACTION_SCREENSHOT" to RiskLevel.LOW,
        "ACTION_TEXT_TO_SPEECH" to RiskLevel.LOW,

        "ACTION_HTTP_REQUEST" to RiskLevel.MEDIUM,
        "ACTION_NETWORK_STATUS" to RiskLevel.LOW,

        "ACTION_GET_TIME" to RiskLevel.LOW,
        "ACTION_RANDOM" to RiskLevel.LOW,

        "ACTION_READ_SCREEN" to RiskLevel.LOW,
        "ACTION_READ_SMS" to RiskLevel.HIGH,         // Privacy-sensitive
        "ACTION_GET_APP_INFO" to RiskLevel.LOW,
        "ACTION_APP_SEARCH" to RiskLevel.LOW,

        "ACTION_TOAST" to RiskLevel.LOW,
        "ACTION_VIBRATE" to RiskLevel.LOW,

        "ACTION_AI_CHAT" to RiskLevel.LOW,

        "ACTION_SEND_NOTIFICATION" to RiskLevel.LOW,
        "ACTION_DYNAMIC_ISLAND" to RiskLevel.LOW,

        "ACTION_CONFIRM" to RiskLevel.LOW,
    )
}
