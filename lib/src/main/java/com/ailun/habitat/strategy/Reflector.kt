package com.ailun.habitat.strategy

import com.ailun.habitat.expression.ExpressionEngine
import com.ailun.habitat.perception.ScreenState
import com.ailun.habitat.planir.Condition
import com.ailun.habitat.planir.PlanStep
import com.ailun.habitat.trajectory.TrajectoryStep

class Reflector(
    private val judgeService: LLMJudgeService? = null,
    private val expressionEngine: ExpressionEngine = ExpressionEngine(),
) {
    suspend fun reflect(
        stepResult: TrajectoryStep,
        expectedPostState: ScreenState?,
        actualPostState: ScreenState?,
        postCondition: Condition?,
    ): ReflectionResult {
        val issues = mutableListOf<String>()

        if (!(stepResult.nodeResult?.success ?: true)) {
            issues.add("Node execution failed: ${stepResult.nodeResult?.error ?: "unknown"}")
        }

        if (expectedPostState != null && actualPostState != null) {
            if (expectedPostState.packageName != actualPostState.packageName) {
                issues.add("Package changed: ${expectedPostState.packageName} → ${actualPostState.packageName}")
            }
            if (actualPostState.clickableElements.isEmpty() && expectedPostState.clickableElements.isNotEmpty()) {
                issues.add("Screen has no interactive elements")
            }
        }

        // Use LLM judge if available
        val judgedSucceeded = if (judgeService != null && expectedPostState != null && actualPostState != null) {
            val judgement = judgeService.judge(
                stepIntent = stepResult.taskDescription ?: stepResult.nodeType,
                preState = expectedPostState,
                postState = actualPostState,
                postCondition = postCondition,
            )
            issues.addAll(judgement.evidence.split("\n").filter { it.isNotBlank() })
            judgement.succeeded
        } else {
            issues.isEmpty() && stepResult.nodeResult?.success != false
        }

        return ReflectionResult(
            stepSucceeded = judgedSucceeded,
            issues = issues,
            suggestedCorrection = null,
            shouldRetry = !judgedSucceeded && issues.isNotEmpty(),
            explanation = issues.joinToString("; "),
        )
    }
}

data class ReflectionResult(
    val stepSucceeded: Boolean,
    val issues: List<String>,
    val suggestedCorrection: PlanStep?,
    val shouldRetry: Boolean,
    val retryStrategy: RetryStrategy? = null,
    val explanation: String,
)

data class RetryStrategy(
    val maxRetries: Int = 3,
    val backoffMs: Long = 1000,
    val alternativeAction: PlanStep? = null,
)
