package com.ailun.habitat

import android.content.Context
import android.util.Log
import com.ailun.habitat.execution.*
import com.ailun.habitat.trajectory.TrajectoryStep
import com.ailun.habitat.trajectory.TrajectoryStore
import kotlinx.coroutines.*

class HabitatExecutor(
    private val factory: NodeHandlerFactory,
    private val maxSteps: Int = 1000,
    private val executionMode: ExecutionMode = ExecutionMode.LIVE_RUN,
    private val executionController: ExecutionController? = null,
    private val trajectoryStore: TrajectoryStore? = null,
    private val strictValidation: Boolean = true,
) {
    companion object { private const val TAG = "HabitatExecutor" }

    private val executorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun execute(graph: WorkflowGraph, workflowContext: WorkflowContext, onLog: ((String) -> Unit)? = null): Job {
        return executorScope.launch { executeSuspend(graph, workflowContext, onLog) }
    }

    fun execute(graph: WorkflowGraph, androidContext: Context, onLog: ((String) -> Unit)? = null): Job {
        val workflowContext = WorkflowContext(androidContext)
        return execute(graph, workflowContext, onLog)
    }

    private suspend fun executeSuspend(
        graph: WorkflowGraph, workflowContext: WorkflowContext, onLog: ((String) -> Unit)? = null,
    ) {
        workflowContext.onLog = onLog

        val validation = WorkflowGraphValidator.validate(graph, factory.registeredTypes())
        validation.warnings.forEach { issue ->
            val nodePrefix = issue.nodeId?.let { "node '$it': " }.orEmpty()
            workflowContext.log("Validation warning: $nodePrefix${issue.message}")
        }
        if (!validation.isValid) {
            validation.errors.forEach { issue ->
                val nodePrefix = issue.nodeId?.let { "node '$it': " }.orEmpty()
                workflowContext.log("Validation error: $nodePrefix${issue.message}")
            }
            if (strictValidation) return
        }

        val nodes = graph.nodes.orEmpty()
        val startId = graph.startNodeId?.trim()
        if (nodes.isEmpty() || startId.isNullOrBlank()) {
            Log.w(TAG, "execute: invalid graph")
            workflowContext.log("Habitat: Invalid graph — missing nodes or start_node_id")
            return
        }

        // Pre-execution check
        if (executionMode != ExecutionMode.LIVE_RUN && executionController != null) {
            when (executionMode) {
                ExecutionMode.DRY_RUN -> {
                    val result = executionController.dryRun(graph)
                    workflowContext.log("Dry-run: ${result.summary}")
                    if (!result.verification.isValid) return
                }
                ExecutionMode.SHADOW_RUN -> {
                    val result = executionController.shadowRun(graph, workflowContext)
                    workflowContext.log("Shadow-run: ${result.steps.size} steps predicted, failures=${result.predictedFailures.size}")
                    if (!result.overallPredictedSuccess) return
                }
                ExecutionMode.SANDBOX_RUN -> {
                    val result = executionController.sandboxRun(graph, workflowContext)
                    workflowContext.log("Sandbox-run completed: $result")
                    if (!result.passed) return
                }
                ExecutionMode.LIVE_RUN -> {} // proceed normally
            }
        }

        val index = HashMap(nodes)
        Log.i(TAG, "=== Workflow start: ${nodes.size} nodes, start=$startId ===")
        workflowContext.log("Graph loaded: ${nodes.size} nodes, starting from '$startId'")

        val runId = workflowContext.workflowId

        // Start trajectory recording AFTER pre-checks (so failed dry-runs don't leak)
        if (executionMode == ExecutionMode.LIVE_RUN) {
            trajectoryStore?.startRun(runId, null)
        }

        var currentId: String? = startId
        var step = 0
        var errorCount = 0

        while (currentId != null && step < maxSteps) {
            coroutineContext.ensureActive()
            val executingId = currentId
            val node = index[executingId]
            if (node == null) {
                val message = "Missing node '$executingId'"
                Log.w(TAG, "step $step: $message")
                workflowContext.variables["_last_error"] = true
                workflowContext.variables["_last_error_msg"] = message
                workflowContext.log("Habitat: $message")
                errorCount++
                break
            }

            val nodeType = node.type?.trim().orEmpty()
            val rawHandler = factory.get(nodeType)
            if (rawHandler == null) {
                val message = "No handler registered for type '$nodeType' (node '$executingId')"
                Log.e(TAG, "step $step: $message")
                workflowContext.variables["_last_error"] = true
                workflowContext.variables["_last_error_msg"] = message
                workflowContext.log(message)
                errorCount++
                break
            }

            Log.i(TAG, "step $step: [$executingId] $nodeType")
            val startTime = System.currentTimeMillis()

            // Adapt handler: V2 → call handleV2 directly, V1 → wrap
            val handlerV2: INodeHandlerV2 = if (rawHandler is INodeHandlerV2) rawHandler
                else LegacyHandlerAdapter(rawHandler)
            val preSnapshot = workflowContext.snapshotVariables()

            val stepResult: StepOutcome = try {
                workflowContext.variables["_last_error"] = false
                workflowContext.variables["_last_error_msg"] = ""
                val result = handlerV2.handleV2(node, workflowContext)
                if (result.success) {
                    val next = result.nextNodeId?.trim()?.takeIf { it.isNotEmpty() }
                    if (next != null && !index.containsKey(next)) {
                        throw IllegalStateException("Node '$executingId' returned missing next node '$next'")
                    }
                    StepOutcome.Continue(next, result)
                } else {
                    workflowContext.variables["_last_error"] = true
                    workflowContext.variables["_last_error_msg"] = result.error ?: "Unknown error"
                    workflowContext.log("Error in '$nodeType': ${result.error}")

                    when {
                        result.rollbackNodeId != null -> {
                            workflowContext.log("→ Rolling back to '${result.rollbackNodeId}'")
                            StepOutcome.Rollback(result.rollbackNodeId, result)
                        }
                        result.compensateAction != null -> {
                            workflowContext.log("→ Compensating with '${result.compensateAction.handlerType}'")
                            StepOutcome.Compensate(result.compensateAction, result)
                        }
                        node.branches?.get("error") != null -> {
                            val errorBranch = node.branches?.get("error")?.trim()?.takeIf { it.isNotEmpty() }
                            workflowContext.log("→ Routing to error branch '$errorBranch'")
                            StepOutcome.Continue(errorBranch, result)
                        }
                        else -> {
                            errorCount++
                            StepOutcome.Stop(result.error ?: "Unknown error", result)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                errorCount++
                val message = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "step $step: [$executingId] $nodeType FAILED: $message", e)
                workflowContext.log("Error in '$nodeType': $message")
                workflowContext.variables["_last_error"] = true
                workflowContext.variables["_last_error_msg"] = message
                val errorBranch = node.branches?.get("error")?.trim()?.takeIf { it.isNotEmpty() }
                if (errorBranch != null) StepOutcome.Continue(errorBranch, NodeResult.error(message))
                else StepOutcome.Stop(message)
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 1000) { Log.i(TAG, "step $step: [$executingId] $nodeType took ${elapsed}ms (slow)") }

            // Record trajectory
            if (trajectoryStore != null) {
                val result = (stepResult as? StepOutcome.Continue)?.result
                    ?: (stepResult as? StepOutcome.Stop)?.result
                trajectoryStore.recordStep(TrajectoryStep(
                    runId = runId, stepIndex = step, taskDescription = node.description ?: nodeType,
                    nodeId = executingId, nodeType = nodeType,
                    actionParams = node.params ?: emptyMap(),
                    preScreenState = null, postScreenState = null,
                    variableDiff = workflowContext.diffSnapshot(preSnapshot),
                    nodeResult = result, expressionEvaluations = emptyList(),
                    riskLabels = result?.riskEvents?.map { it.description } ?: emptyList(),
                    confirmationDecisions = emptyList(),
                    timestampMs = startTime, durationMs = elapsed,
                ))
            }

            currentId = when (stepResult) {
                is StepOutcome.Continue -> stepResult.nextNodeId
                is StepOutcome.Rollback -> stepResult.rollbackNodeId
                is StepOutcome.Compensate -> run {
                    // Execute compensation inline, supporting both V1 and V2 handlers
                    val compRaw = factory.get(stepResult.action.handlerType)
                    if (compRaw != null) {
                        val compNode = WorkflowNode().apply {
                            id = "compensate_$executingId"; type = stepResult.action.handlerType
                            params = stepResult.action.params
                        }
                        try {
                            if (compRaw is INodeHandlerV2) {
                                compRaw.handleV2(compNode, workflowContext)
                            } else {
                                compRaw.handle(compNode, workflowContext)
                            }
                        } catch (compError: Exception) {
                            workflowContext.log("Compensation failed: ${compError.message}")
                        }
                    }
                    null
                }
                is StepOutcome.Stop -> null
            }
            step++
        }

        if (step >= maxSteps) {
            Log.w(TAG, "=== Aborted after $maxSteps steps ===")
            workflowContext.variables["_last_error"] = true
            workflowContext.variables["_last_error_msg"] = "Step limit $maxSteps reached"
            workflowContext.log("Habitat: Aborted (step limit $maxSteps reached)")
        } else {
            Log.i(TAG, "=== Finished: $step steps, $errorCount errors ===")
            workflowContext.log("Habitat: Finished ($step steps, $errorCount errors)")
        }

        if (executionMode == ExecutionMode.LIVE_RUN) {
            trajectoryStore?.endRun(runId, null)
        }
    }
}

sealed class StepOutcome {
    data class Continue(val nextNodeId: String?, val result: NodeResult? = null) : StepOutcome()
    data class Rollback(val rollbackNodeId: String, val result: NodeResult? = null) : StepOutcome()
    data class Compensate(val action: CompensateAction, val result: NodeResult? = null) : StepOutcome()
    data class Stop(val reason: String?, val result: NodeResult? = null) : StepOutcome()
}
