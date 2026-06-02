package com.ailun.habitat.capability

/**
 * Risk level for workflows, nodes, and capabilities.
 *
 * Used by RiskEngine to grade nodes, by ConfirmationManager to gate execution,
 * and by the Agent Debugger to color-code risk badges.
 */
enum class RiskLevel(val score: Int, val label: String) {
    /** Safe read-only operations. No user confirmation needed. */
    LOW(0, "Low"),

    /** Potentially impactful but recoverable. May prompt for first-time use. */
    MEDIUM(5, "Medium"),

    /** Could cause data loss, privacy leak, or unwanted charges. Requires confirmation. */
    HIGH(10, "High"),

    /** Irreversible system-level action. Requires explicit user token. */
    CRITICAL(20, "Critical");

    companion object {
        /** The threshold above which user confirmation is required. */
        const val CONFIRMATION_THRESHOLD_SCORE = 10  // >= HIGH

        fun max(a: RiskLevel, b: RiskLevel): RiskLevel =
            if (a.score >= b.score) a else b
    }
}
