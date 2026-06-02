package com.ailun.habitat.strategy

import com.ailun.habitat.ai.ILLMService
import com.ailun.habitat.expression.ExpressionResult
import com.ailun.habitat.perception.ScreenState
import com.ailun.habitat.trajectory.TrajectoryStep

class LLMRepairService(private val llmService: ILLMService) {
    suspend fun suggestRepair(
        failedStep: TrajectoryStep,
        errorContext: RepairContext,
    ): RepairSuggestion? {
        if (!llmService.isReady()) return null

        val prompt = buildString {
            appendLine("A mobile automation step failed. Suggest a repair.")
            appendLine()
            appendLine("Failed step: ${failedStep.taskDescription}")
            appendLine("Node type: ${failedStep.nodeType}")
            appendLine("Error: ${failedStep.nodeResult?.error ?: "unknown"}")
            appendLine()
            appendLine("Current screen state: ${errorContext.currentScreenState?.packageName}/${errorContext.currentScreenState?.activityName}")
            appendLine("Visible elements: ${errorContext.currentScreenState?.clickableElements?.take(10)?.joinToString { it.selector } ?: "none"}")
            appendLine()
            appendLine("Suggest: 1) Alternative action, 2) Adjusted parameters, 3) Skip this step")
            appendLine("Respond with JSON: {\"alternative_action_type\": \"...\", \"alternative_params\": {...}, \"suggestion\": \"...\", \"confidence\": 0.0-1.0}")
        }

        val response = llmService.chat(prompt)

        return try {
            val json = com.google.gson.JsonParser.parseString(response).asJsonObject
            RepairSuggestion(
                alternativeApproach = json.get("suggestion")?.asString ?: response,
                confidence = json.get("confidence")?.asFloat ?: 0.5f,
            )
        } catch (_: Exception) {
            RepairSuggestion(
                alternativeApproach = response,
                confidence = 0.3f,
            )
        }
    }
}

data class RepairContext(
    val currentScreenState: ScreenState?,
    val previousSteps: List<TrajectoryStep> = emptyList(),
    val errorMessage: String = "",
    val expressionEvaluationResults: List<ExpressionResult> = emptyList(),
)

data class RepairSuggestion(
    val alternativeApproach: String?,
    val confidence: Float,
)
