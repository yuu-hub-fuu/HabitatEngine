package com.ailun.habitat.strategy

import com.ailun.habitat.ai.ILLMService
import com.ailun.habitat.perception.ScreenState
import com.ailun.habitat.planir.Condition

class LLMJudgeService(private val llmService: ILLMService) {
    suspend fun judge(
        stepIntent: String,
        preState: ScreenState,
        postState: ScreenState,
        postCondition: Condition?,
    ): JudgementResult {
        if (!llmService.isReady()) {
            return JudgementResult(true, 0.5f, "LLM not ready", "Skipped LLM judge")
        }

        val prompt = buildString {
            appendLine("You are a mobile automation judge. Determine if this action succeeded.")
            appendLine()
            appendLine("Action intent: $stepIntent")
            if (postCondition != null) {
                appendLine("Expected outcome: ${postCondition.description}")
            }
            appendLine()
            appendLine("Before state:")
            appendLine("  App: ${preState.packageName}/${preState.activityName}")
            appendLine("  Visible elements: ${preState.clickableElements.take(5).joinToString { it.selector }}")
            appendLine()
            appendLine("After state:")
            appendLine("  App: ${postState.packageName}/${postState.activityName}")
            appendLine("  Visible elements: ${postState.clickableElements.take(5).joinToString { it.selector }}")
            appendLine()
            appendLine("Respond with JSON: {\"succeeded\": true/false, \"confidence\": 0.0-1.0, \"evidence\": \"...\", \"reasoning\": \"...\"}")
        }

        val response = llmService.chat(prompt)
        return parseJudgement(response)
    }

    private fun parseJudgement(response: String): JudgementResult {
        return try {
            val json = com.google.gson.JsonParser.parseString(response).asJsonObject
            JudgementResult(
                succeeded = json.get("succeeded")?.asBoolean ?: true,
                confidence = json.get("confidence")?.asFloat ?: 0.5f,
                evidence = json.get("evidence")?.asString ?: "",
                reasoning = json.get("reasoning")?.asString ?: response,
            )
        } catch (_: Exception) {
            JudgementResult(true, 0.5f, "Failed to parse LLM response", response)
        }
    }
}

data class JudgementResult(
    val succeeded: Boolean,
    val confidence: Float,
    val evidence: String,
    val reasoning: String,
)
