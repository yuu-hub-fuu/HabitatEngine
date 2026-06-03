package com.ailun.habitat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

class HabitatExecutor(
    private val factory: NodeHandlerFactory,
    private val maxSteps: Int = 1000,
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

        val nodes = graph.nodes.orEmpty()
        val startId = graph.startNodeId?.trim()
        if (nodes.isEmpty() || startId.isNullOrBlank()) {
            Log.w(TAG, "execute: invalid graph")
            workflowContext.log("Habitat: Invalid graph — missing nodes or start_node_id")
            return
        }

        val index = HashMap(nodes)
        Log.i(TAG, "=== Workflow start: ${nodes.size} nodes, start=$startId ===")
        workflowContext.log("Graph loaded: ${nodes.size} nodes, starting from '$startId'")

        val runId = workflowContext.workflowId
        var currentId: String? = startId
        var step = 0
        var errorCount = 0

        while (currentId != null && step < maxSteps) {
            coroutineContext.ensureActive()
            val currentIdBeforeRun = currentId ?: ""
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

            // ── Pre-condition check ──
            node.preCondition?.let { expr ->
                val pre = ExpressionEngine.eval(expr, workflowContext)
                workflowContext.log("PreCondition: ${pre.explanation}")
                if (!pre.value) {
                    currentId = node.branches?.get("pre_failed") ?: null
                    step++
                    continue
                }
            }

            // ── Risk gating ──
            val risk = RiskEngine.assess(node, workflowContext)
            if (risk?.requiresConfirmation == true) {
                workflowContext.log("⚠ Risk blocked: ${risk.level} ${risk.reason}")
                workflowContext.putVariable("_last_risk_blocked", true)
                workflowContext.putVariable("_last_risk_reason", risk.reason)
                currentId = node.branches?.get("blocked") ?: null
                step++
                continue
            }

            val nodeType = node.type?.trim().orEmpty()
            val handler = factory.get(nodeType)
            if (handler == null) {
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

            try {
                val result = handler.handle(node, workflowContext)

                result.outputVariables.forEach { (k, v) ->
                    workflowContext.putVariable(k, v)
                }

                workflowContext.putVariable("_last_success", result.success)
                workflowContext.putVariable("_last_error", !result.success)
                workflowContext.putVariable("_last_error_msg", result.errorMessage ?: "")

                if (result.riskEvent != null) {
                    workflowContext.putVariable("_last_risk_level", result.riskEvent.level.name)
                    workflowContext.putVariable("_last_risk_capability", result.riskEvent.capability)
                }

                // ── Post-condition check ──
                if (result.success && node.postCondition != null) {
                    val post = ExpressionEngine.eval(node.postCondition!!, workflowContext)
                    workflowContext.log("PostCondition: ${post.explanation}")
                    if (!post.value) {
                        workflowContext.putVariable("_last_error", true)
                        workflowContext.putVariable("_last_error_msg", "post_condition failed: ${node.postCondition}")
                        currentId = node.branches?.get("post_failed")
                            ?: node.branches?.get("error")
                    } else {
                        currentId = result.nextNodeId
                    }
                } else {
                    currentId = result.nextNodeId
                }

                // ── Trajectory recording ──
                TrajectoryStore.add(
                    TrajectoryStep(
                        workflowId = runId,
                        stepIndex = step,
                        nodeId = currentIdBeforeRun,
                        nodeType = nodeType,
                        success = result.success,
                        nextNodeId = currentId,
                        errorMessage = result.errorMessage,
                        variablesSnapshot = workflowContext.variables.toMap()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCount++
                workflowContext.putVariable("_last_success", false)
                workflowContext.putVariable("_last_error", true)
                workflowContext.putVariable("_last_error_msg", e.message ?: "Unknown error")
                workflowContext.log("✗ Error in '$nodeType': ${e.message}")

                val errorBranch = node.branches?.get("error")
                currentId = errorBranch

                TrajectoryStore.add(
                    TrajectoryStep(
                        workflowId = runId,
                        stepIndex = step,
                        nodeId = currentIdBeforeRun,
                        nodeType = nodeType,
                        success = false,
                        nextNodeId = errorBranch,
                        errorMessage = e.message,
                        variablesSnapshot = workflowContext.variables.toMap()
                    )
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 1000) {
                Log.i(TAG, "step $step: [$executingId] $nodeType took ${elapsed}ms (slow)")
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

        // Reset confirmation tokens
        factory.confirmationManager?.reset()
    }
}
