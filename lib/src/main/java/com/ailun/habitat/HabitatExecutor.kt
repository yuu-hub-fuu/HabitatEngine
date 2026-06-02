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
        val nodes = graph.nodes
        val startId = graph.startNodeId
        if (nodes.isNullOrEmpty() || startId.isNullOrBlank()) {
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
                    workflowContext.log("Shadow-run: ${result.steps.size} steps predicted")
                }
                ExecutionMode.SANDBOX_RUN -> {
                    val result = executionController.sandboxRun(graph, workflowContext)
                    workflowContext.log("Sandbox-run completed: $result")
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
            val node = index[currentId]
            if (node == null) {
                Log.w(TAG, "step $step: node '$currentId' not found in graph")
                workflowContext.log("Habitat: Missing node '$currentId'")
                break
            }

            val nodeType = node.type ?: "<null type>"
            val rawHandler = factory.get(nodeType)
            if (rawHandler == null) {
                Log.w(TAG, "step $step: node '$currentId' type '$nodeType' — no handler registered")
                workflowContext.log("No handler for type '$nodeType' (node '$currentId'), skipping")
                currentId = node.next; step++; continue
            }

            Log.i(TAG, "step $step: [$currentId] $nodeType")
            val startTime = System.currentTimeMillis()

            // Adapt handler: V2 → call handleV2 directly, V1 → wrap
            val handlerV2: INodeHandlerV2 = if (rawHandler is INodeHandlerV2) rawHandler
                else LegacyHandlerAdapter(rawHandler)
            val preSnapshot = workflowContext.snapshotVariables()

            val stepResult: StepOutcome = try {
                val result = handlerV2.handleV2(node, workflowContext)
                if (result.success) {
                    StepOutcome.Continue(result.nextNodeId, result)
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
                        else -> {
                            errorCount++
                            StepOutcome.Stop(result.error ?: "Unknown error", result)
                        }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                errorCount++
                Log.e(TAG, "step $step: [$currentId] $nodeType FAILED: ${e.message}", e)
                workflowContext.log("Error in '$nodeType': ${e.message}")
                workflowContext.variables["_last_error"] = true
                workflowContext.variables["_last_error_msg"] = e.message ?: ""
                StepOutcome.Stop(e.message ?: "Handler exception")
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 1000) { Log.i(TAG, "step $step: [$currentId] $nodeType took ${elapsed}ms (slow)") }

            // Record trajectory
            if (trajectoryStore != null) {
                val result = (stepResult as? StepOutcome.Continue)?.result
                    ?: (stepResult as? StepOutcome.Stop)?.result
                trajectoryStore.recordStep(TrajectoryStep(
                    runId = runId, stepIndex = step, taskDescription = node.description ?: nodeType,
                    nodeId = currentId, nodeType = nodeType,
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
                            id = "compensate_$currentId"; type = stepResult.action.handlerType
                            params = stepResult.action.params
                        }
                        try {
                            if (compRaw is INodeHandlerV2) {
                                compRaw.handleV2(compNode, workflowContext)
                            } else {
                                compRaw.handle(compNode, workflowContext)
                            }
                        } catch (_: Exception) {}
                    }
                    null
                }
                is StepOutcome.Stop -> null
            }
            step++
        }

        if (step >= maxSteps) {
            Log.w(TAG, "=== Aborted after $maxSteps steps ===")
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
