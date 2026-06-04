package com.ailun.habitat

import android.content.Context
import android.util.Log
import com.ailun.habitat.capability.RiskEngine
import com.ailun.habitat.expression.ExpressionEngine
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

class HabitatExecutor(
    private val factory: NodeHandlerFactory,
    private val maxSteps: Int = 1000,
) {
    companion object { private const val TAG = "HabitatExecutor" }

    private val executorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val exprEngine = ExpressionEngine()
    private val riskEngine = RiskEngine()

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

        // Pre-compute risk assessments for all nodes (capability.RiskEngine)
        val riskCache = mutableMapOf<String, com.ailun.habitat.capability.NodeRiskAssessment>()
        for ((nid, n) in nodes) {
            riskCache[nid] = riskEngine.assessNode(n)
        }

        while (currentId != null && step < maxSteps) {
            coroutineContext.ensureActive()
            val currentIdBeforeRun = currentId ?: ""
            val executingId = currentId
            val node = index[executingId]

            if (node == null) {
                val message = "Missing node '$executingId'"
                Log.w(TAG, "step $step: $message")
                workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, message)
                workflowContext.log("Habitat: $message")
                errorCount++
                TrajectoryStore.add(TrajectoryStep(
                    workflowId = runId, stepIndex = step, nodeId = currentIdBeforeRun,
                    nodeType = "", success = false, nextNodeId = null,
                    errorMessage = message, variablesSnapshot = workflowContext.variables.toMap()
                ))
                break
            }

            // ── Pre-condition check ──
            node.preCondition?.let { expr ->
                val pre = exprEngine.evaluate(expr, workflowContext)
                workflowContext.log("PreCondition: ${pre.explanation}")
                if (!pre.booleanResult) {
                    workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                    workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, "pre_condition failed: $expr")
                    val preFailedTarget = node.branches?.get("pre_failed") ?: null
                    TrajectoryStore.add(TrajectoryStep(
                        workflowId = runId, stepIndex = step, nodeId = currentIdBeforeRun,
                        nodeType = node.type ?: "", success = false, nextNodeId = preFailedTarget,
                        errorMessage = "pre_condition failed", variablesSnapshot = workflowContext.variables.toMap()
                    ))
                    currentId = preFailedTarget
                    step++
                    continue
                }
            }

            // ── Risk gating via capability.RiskEngine → ConfirmationManager ──
            val nodeRisk = riskCache[executingId]
            if (nodeRisk?.requiresConfirmation == true) {
                val confManager = factory.confirmationManager
                if (confManager != null) {
                    // Confirmation flow: ask user, block only if denied
                    val approved = confManager.ensureConfirmed(node, emptyMap())
                    if (!approved) {
                        workflowContext.log("⛔ Risk denied: ${nodeRisk.riskLevel.label} ${nodeRisk.riskReasons.joinToString()}")
                        workflowContext.putVariable(RuntimeVars.LAST_SUCCESS, false)
                        workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                        workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, "User denied risky action: ${nodeRisk.riskReasons.joinToString("; ")}")
                        workflowContext.putVariable(RuntimeVars.LAST_RISK_BLOCKED, true)
                        workflowContext.putVariable(RuntimeVars.LAST_RISK_REASON, nodeRisk.riskReasons.joinToString("; "))
                        val blockedTarget = node.branches?.get("blocked") ?: null
                        TrajectoryStore.add(TrajectoryStep(
                            workflowId = runId, stepIndex = step, nodeId = currentIdBeforeRun,
                            nodeType = node.type ?: "", success = false, nextNodeId = blockedTarget,
                            errorMessage = "risk denied: ${nodeRisk.riskReasons.joinToString("; ")}",
                            variablesSnapshot = workflowContext.variables.toMap()
                        ))
                        currentId = blockedTarget
                        step++
                        continue
                    }
                    workflowContext.log("✓ Risk confirmed by user: ${nodeRisk.riskLevel.label}")
                } else {
                    // No confirmation provider → fail closed for high-risk nodes
                    workflowContext.log("⛔ Risk blocked (no provider): ${nodeRisk.riskLevel.label} ${nodeRisk.riskReasons.joinToString()}")
                    workflowContext.putVariable(RuntimeVars.LAST_SUCCESS, false)
                    workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                    workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, "High-risk node '${node.type}' blocked — no confirmation provider available")
                    workflowContext.putVariable(RuntimeVars.LAST_RISK_BLOCKED, true)
                    workflowContext.putVariable(RuntimeVars.LAST_RISK_REASON, nodeRisk.riskReasons.joinToString("; "))
                    val blockedTarget = node.branches?.get("blocked") ?: null
                    TrajectoryStore.add(TrajectoryStep(
                        workflowId = runId, stepIndex = step, nodeId = currentIdBeforeRun,
                        nodeType = node.type ?: "", success = false, nextNodeId = blockedTarget,
                        errorMessage = "risk blocked (no confirmation provider): ${nodeRisk.riskReasons.joinToString("; ")}",
                        variablesSnapshot = workflowContext.variables.toMap()
                    ))
                    currentId = blockedTarget
                    step++
                    continue
                }
            }

            // ── Capability authorization check ──
            val requiredCaps = node.requiredCapabilities.orEmpty()
            val graphCaps = graph.capabilities.orEmpty().toSet()
            if (requiredCaps.isNotEmpty()) {
                val missingCaps = requiredCaps.filter { it !in graphCaps }
                if (missingCaps.isNotEmpty()) {
                    val msg = "Node '${executingId}' requires capabilities $missingCaps, but workflow only grants $graphCaps"
                    workflowContext.log("⛔ Capability denied: $msg")
                    workflowContext.putVariable(RuntimeVars.LAST_SUCCESS, false)
                    workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                    workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, msg)
                    val deniedTarget = node.branches?.get("blocked") ?: node.branches?.get("error") ?: null
                    TrajectoryStore.add(TrajectoryStep(
                        workflowId = runId, stepIndex = step, nodeId = currentIdBeforeRun,
                        nodeType = node.type ?: "", success = false, nextNodeId = deniedTarget,
                        errorMessage = msg, variablesSnapshot = workflowContext.variables.toMap()
                    ))
                    currentId = deniedTarget
                    step++
                    continue
                }
            }

            val nodeType = node.type?.trim().orEmpty()
            val handler = factory.get(nodeType)
            if (handler == null) {
                val message = "No handler registered for type '$nodeType' (node '$executingId')"
                Log.e(TAG, "step $step: $message")
                workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, message)
                workflowContext.log(message)
                errorCount++
                TrajectoryStore.add(TrajectoryStep(
                    workflowId = runId, stepIndex = step, nodeId = currentIdBeforeRun,
                    nodeType = nodeType, success = false, nextNodeId = null,
                    errorMessage = message, variablesSnapshot = workflowContext.variables.toMap()
                ))
                break
            }

            Log.i(TAG, "step $step: [$executingId] $nodeType")
            val startTime = System.currentTimeMillis()

            try {
                val result = handler.handle(node, workflowContext)

                result.outputVariables.forEach { (k, v) ->
                    workflowContext.putVariable(k, v)
                }

                workflowContext.putVariable(RuntimeVars.LAST_SUCCESS, result.success)
                workflowContext.putVariable(RuntimeVars.LAST_ERROR, !result.success)
                workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, result.errorMessage ?: "")

                if (result.riskEvent != null) {
                    workflowContext.putVariable(RuntimeVars.LAST_RISK_LEVEL, result.riskEvent.level.name)
                    workflowContext.putVariable(RuntimeVars.LAST_RISK_CAPABILITY, result.riskEvent.capability)
                }

                // ── Post-condition check ──
                var effectiveSuccess = result.success
                if (result.success && node.postCondition != null) {
                    val post = exprEngine.evaluate(node.postCondition!!, workflowContext)
                    workflowContext.log("PostCondition: ${post.explanation}")
                    if (!post.booleanResult) {
                        effectiveSuccess = false
                        workflowContext.putVariable(RuntimeVars.LAST_SUCCESS, false)
                        workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                        workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, "post_condition failed: ${node.postCondition}")
                        currentId = node.branches?.get("post_failed")
                            ?: node.branches?.get("error")
                    } else {
                        currentId = result.nextNodeId
                    }
                } else {
                    currentId = result.nextNodeId
                }

                // ── Trajectory recording (uses effectiveSuccess) ──
                TrajectoryStore.add(
                    TrajectoryStep(
                        workflowId = runId,
                        stepIndex = step,
                        nodeId = currentIdBeforeRun,
                        nodeType = nodeType,
                        success = effectiveSuccess,
                        nextNodeId = currentId,
                        errorMessage = if (effectiveSuccess) result.errorMessage
                            else workflowContext.getVariable(RuntimeVars.LAST_ERROR_MSG) as? String,
                        variablesSnapshot = workflowContext.variables.toMap()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCount++
                workflowContext.putVariable(RuntimeVars.LAST_SUCCESS, false)
                workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
                workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, e.message ?: "Unknown error")
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

        // ── Step limit reached ──
        if (step >= maxSteps) {
            Log.w(TAG, "=== Aborted after $maxSteps steps ===")
            workflowContext.putVariable(RuntimeVars.LAST_ERROR, true)
            workflowContext.putVariable(RuntimeVars.LAST_ERROR_MSG, "Step limit $maxSteps reached")
            workflowContext.log("Habitat: Aborted (step limit $maxSteps reached)")
            TrajectoryStore.add(TrajectoryStep(
                workflowId = runId, stepIndex = step, nodeId = "_step_limit",
                nodeType = "_step_limit", success = false, nextNodeId = null,
                errorMessage = "Step limit $maxSteps reached",
                variablesSnapshot = workflowContext.variables.toMap()
            ))
        } else {
            Log.i(TAG, "=== Finished: $step steps, $errorCount errors ===")
            workflowContext.log("Habitat: Finished ($step steps, $errorCount errors)")
        }

        // ── Graph-level success criteria evaluation ──
        graph.successCriteria?.let { criteria ->
            val conditions = criteria["conditions"] as? List<*> ?: emptyList<Any>()
            var allMet = conditions.isNotEmpty()
            for (cond in conditions) {
                val condMap = cond as? Map<*, *> ?: continue
                val expr = condMap["expression"]?.toString() ?: continue
                val evalResult = exprEngine.evaluate(expr, workflowContext)
                if (!evalResult.booleanResult) {
                    allMet = false
                    workflowContext.log("SuccessCriteria FAILED: $expr → ${evalResult.explanation}")
                }
            }
            workflowContext.putVariable(RuntimeVars.WORKFLOW_SUCCESS, allMet)
            workflowContext.log("Workflow success criteria: ${if (allMet) "PASSED" else "FAILED"}")
        }

        // Reset confirmation tokens
        factory.confirmationManager?.reset()
    }
}
