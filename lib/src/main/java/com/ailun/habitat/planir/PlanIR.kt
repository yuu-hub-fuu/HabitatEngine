package com.ailun.habitat.planir

/**
 * Plan Intermediate Representation — structured planning output from LLM.
 *
 * Instead of forcing the LLM to generate raw Habitat workflow JSON directly,
 * the LLM generates a PlanIR which is then validated and compiled into a
 * valid flat WorkflowGraph. This reduces LLM JSON errors and enables
 * automatic insertion of safety features (confirmations, error handling).
 */
data class PlanIR(
    val version: String = "1.0",
    val task: TaskGoal,
    val preconditions: List<Condition> = emptyList(),
    val postconditions: List<Condition> = emptyList(),
    val riskLevel: String = "LOW",  // "LOW", "MEDIUM", "HIGH", "CRITICAL" — parsed to RiskLevel later
    val requiredCapabilities: List<String> = emptyList(),  // List for Gson compat
    val failureRecovery: String = "ABORT",  // ABORT, RETRY_ONCE, SKIP_AND_CONTINUE
    val steps: List<PlanStep> = emptyList(),
)

data class TaskGoal(
    val description: String,
    val app: String? = null,
    val entryPage: String? = null,
    val successCriteria: List<Condition> = emptyList(),
)

data class Condition(
    val type: String,       // SCREEN_CONTAINS, VARIABLE_SATISFIES, FILE_EXISTS, HTTP_RESULT, ELEMENT_VISIBLE
    val expression: String, // Expression-engine compatible string
    val description: String = "",
)

data class PlanStep(
    val id: String,
    val intent: String,
    val actionType: String,
    val params: Map<String, Any> = emptyMap(),
    val preCondition: Condition? = null,
    val postCondition: Condition? = null,
    val onFailure: String = "ABORT",
    val requireConfirmation: Boolean = false,
    val timeoutMs: Long? = null,
    val outputVar: String? = null,
)

/**
 * Result of compiling a PlanIR to a flat WorkflowGraph.
 */
data class CompilationResult(
    val graph: com.ailun.habitat.WorkflowGraph,
    val warnings: List<String> = emptyList(),
    val insertedConfirmations: List<String> = emptyList(),
    val generatedErrorHandlers: List<String> = emptyList(),
    val nodeMapping: Map<String, String> = emptyMap(),
)

/**
 * Result of validating a PlanIR before compilation.
 */
data class PlanIRValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val suggestedFixes: List<String> = emptyList(),
)
