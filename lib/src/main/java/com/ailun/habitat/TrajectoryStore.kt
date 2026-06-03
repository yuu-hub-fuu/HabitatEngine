package com.ailun.habitat

data class TrajectoryStep(
    val workflowId: String,
    val stepIndex: Int,
    val nodeId: String,
    val nodeType: String,
    val success: Boolean,
    val nextNodeId: String?,
    val errorMessage: String?,
    val variablesSnapshot: Map<String, Any?>,
    val timestamp: Long = System.currentTimeMillis()
)

object TrajectoryStore {
    private val steps = mutableListOf<TrajectoryStep>()

    @Synchronized
    fun add(step: TrajectoryStep) {
        steps += step
        if (steps.size > 5000) {
            steps.removeAt(0)
        }
    }

    @Synchronized
    fun getRecent(limit: Int = 100): List<TrajectoryStep> =
        steps.takeLast(limit)

    @Synchronized
    fun clear() = steps.clear()
}
