package com.ailun.habitat.strategy

import com.ailun.habitat.HabitatExecutor
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.planir.*
import com.ailun.habitat.trajectory.AgentRunResult
import com.ailun.habitat.trajectory.TrajectoryStore

class AgentLoop(
    private val planner: IPlanner,
    private val executor: HabitatExecutor,
    private val observer: Observer,
    private val reflector: Reflector,
    private val planIRCompiler: PlanIRCompiler,
    private val trajectoryStore: TrajectoryStore? = null,
) {
    suspend fun run(task: TaskGoal, baseContext: WorkflowContext): AgentRunResult {
        val trajectoryId = baseContext.workflowId
        var stepsExecuted = 0
        var stepsRetried = 0

        baseContext.log("AgentLoop: Planning task '${task.description}'")
        trajectoryStore?.startRun(trajectoryId, task)

        // 1. Plan
        val planContext = PlanningContext(maxSteps = 30)
        val planResult = planner.plan(task, planContext)
        baseContext.log("  Plan: ${planResult.planIR.steps.size} steps, confidence=${planResult.confidence}")

        // 2. Compile
        val compiled = planIRCompiler.compile(planResult.planIR)
        baseContext.log("  Compiled: ${compiled.graph.nodes?.size ?: 0} nodes")
        compiled.warnings.forEach { baseContext.log("  ⚠ $it") }

        // 3. Execute
        val job = executor.execute(compiled.graph, baseContext)
        job.join()
        stepsExecuted = compiled.graph.nodes?.size ?: 0

        // 4. Observe final state
        val finalState = observer.observe()

        // 5. Reflect on overall result
        val finalStateDesc = when {
            finalState != null -> "${finalState.packageName}/${finalState.activityName} with ${finalState.clickableElements.size} elements"
            else -> "unknown"
        }
        val success = job.isCompleted && !job.isCancelled

        val summary = buildString {
            appendLine("Task: ${task.description}")
            appendLine("Plan confidence: ${planResult.confidence}")
            appendLine("Steps planned: ${planResult.planIR.steps.size}")
            appendLine("Steps executed: $stepsExecuted")
            appendLine("Steps retried: $stepsRetried")
            appendLine("Final state: $finalStateDesc")
            appendLine("Result: ${if (success) "SUCCESS" else "FAILED"}")
        }

        val result = AgentRunResult(
            success = success,
            stepsExecuted = stepsExecuted,
            stepsRetried = stepsRetried,
            finalStateDescription = finalStateDesc,
            trajectoryId = trajectoryId,
            summary = summary,
        )

        trajectoryStore?.endRun(trajectoryId, result)
        return result
    }
}
