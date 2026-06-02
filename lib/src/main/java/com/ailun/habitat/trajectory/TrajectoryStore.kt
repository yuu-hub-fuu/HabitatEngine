package com.ailun.habitat.trajectory

import com.ailun.habitat.planir.TaskGoal

data class AgentRunResult(
    val success: Boolean,
    val stepsExecuted: Int,
    val stepsRetried: Int,
    val finalStateDescription: String? = null,
    val trajectoryId: String,
    val summary: String,
)

data class RunSummary(
    val runId: String,
    val taskDescription: String?,
    val stepCount: Int,
    val success: Boolean,
    val durationMs: Long,
    val errorCount: Int,
    val startedAt: Long,
)

class TrajectoryStore(private val maxRuns: Int = 100) {
    private val runs = mutableMapOf<String, MutableList<TrajectoryStep>>()
    private val summaries = mutableMapOf<String, AgentRunResult>()

    fun startRun(runId: String, task: TaskGoal?) {
        runs.getOrPut(runId) { mutableListOf() }
    }

    fun recordStep(step: TrajectoryStep) {
        val list = runs.getOrPut(step.runId) { mutableListOf() }
        list.add(step)
        // Enforce max runs
        while (runs.size > maxRuns) {
            runs.entries.firstOrNull()?.let { runs.remove(it.key) }
        }
    }

    fun endRun(runId: String, result: AgentRunResult? = null) {
        if (result != null) summaries[runId] = result
    }

    fun getRun(runId: String): List<TrajectoryStep> = runs[runId] ?: emptyList()

    fun getRecentRuns(limit: Int = 20): List<RunSummary> {
        return runs.entries.takeLast(limit).map { (id, steps) ->
            val firstStep = steps.firstOrNull()
            val lastStep = steps.lastOrNull()
            RunSummary(
                runId = id,
                taskDescription = firstStep?.taskDescription,
                stepCount = steps.size,
                success = summaries[id]?.success ?: false,
                durationMs = (lastStep?.timestampMs ?: 0L) - (firstStep?.timestampMs ?: 0L),
                errorCount = steps.count { it.nodeResult?.success == false },
                startedAt = firstStep?.timestampMs ?: 0L,
            )
        }
    }

    fun exportRun(runId: String): String {
        val steps = runs[runId] ?: return "[]"
        val sb = StringBuilder("[\n")
        steps.forEachIndexed { i, step ->
            sb.append("  { \"step\": ${step.stepIndex}, \"node\": \"${step.nodeId}\", ")
            sb.append("\"type\": \"${step.nodeType}\", \"success\": ${step.nodeResult?.success ?: false}")
            if (step.nodeResult?.error != null) {
                sb.append(", \"error\": \"${step.nodeResult.error}\"")
            }
            sb.append(" }")
            if (i < steps.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    fun clear() { runs.clear(); summaries.clear() }
}
