package com.ailun.habitat.strategy

import com.ailun.habitat.perception.PerceptionEngine
import com.ailun.habitat.perception.ScreenState
import com.ailun.habitat.perception.ScreenStateDiff
import com.ailun.habitat.planir.Condition

class Observer(
    private val perceptionEngine: PerceptionEngine?,
) {
    suspend fun observe(): ScreenState? = perceptionEngine?.capture()

    fun compare(before: ScreenState, after: ScreenState): ScreenStateDiff =
        perceptionEngine?.diffState(before, after) ?: ScreenStateDiff()

    suspend fun verifyPostCondition(state: ScreenState, condition: Condition): Boolean {
        // Check if condition expression is satisfied by the current state
        return when (condition.type) {
            "SCREEN_CONTAINS" -> {
                val text = state.ocrResult?.fullText ?: ""
                text.contains(condition.expression, ignoreCase = true)
            }
            "ELEMENT_VISIBLE" -> {
                state.findCandidates(condition.expression).isNotEmpty()
            }
            "VARIABLE_SATISFIES" -> true // Evaluated by SuccessEvaluator against context
            else -> true
        }
    }
}
