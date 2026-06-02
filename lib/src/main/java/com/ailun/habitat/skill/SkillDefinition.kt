package com.ailun.habitat.skill

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.success.SuccessCriteria

/**
 * A reusable skill — a curated sub-workflow that performs a common task.
 *
 * Skills are stored in the [SkillRegistry] and can be invoked via:
 * - `ACTION_CALL_SKILL` in flat JSON workflows
 * - `SkillRef` in hierarchical graphs
 * - Direct SkillCompiler.expand() call
 */
data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val applicableApp: String? = null,
    val entryPageDescription: String = "",
    val requiredScreenFeatures: List<String> = emptyList(),
    val subGraph: WorkflowGraph,
    val successCriteria: SuccessCriteria = SuccessCriteria(emptyList()),
    val failureExamples: List<String> = emptyList(),
    val version: Int = 1,
    val requiredCapabilities: List<String> = emptyList(),
)
