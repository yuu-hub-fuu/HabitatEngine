package com.ailun.habitat.execution

import com.ailun.habitat.capability.RiskLevel

/**
 * Moved to com.ailun.habitat.NodeResult. This file retains only utility types.
 */
data class DiffEntry(val before: Any?, val after: Any?)

data class CompensateAction(
    val handlerType: String,
    val params: Map<String, Any>,
)
