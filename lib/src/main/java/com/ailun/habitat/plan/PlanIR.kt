package com.ailun.habitat.plan

data class PlanIR(
    val goal: String,
    val steps: List<PlanStepIR>,
    val requiredCapabilities: List<String> = emptyList(),
    val successCriteria: String? = null
)

data class PlanStepIR(
    val id: String,
    val intent: String,
    val actionType: String,
    val params: Map<String, Any?> = emptyMap(),
    val preCondition: String? = null,
    val postCondition: String? = null,
    val riskLevel: String? = null,
    val onSuccess: String? = null,
    val onFailure: String? = null
)
