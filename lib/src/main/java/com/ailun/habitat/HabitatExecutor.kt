package com.ailun.habitat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class HabitatExecutor(
    private val factory: NodeHandlerFactory,
    private val maxSteps: Int = 1000,
    private val strictValidation: Boolean = true,
) {

    companion object {
        private const val TAG = "HabitatExecutor"
    }

    private val executorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun execute(
        graph: WorkflowGraph,
        workflowContext: WorkflowContext,
        onLog: ((String) -> Unit)? = null,
    ): Job {
        return executorScope.launch {
            executeSuspend(graph, workflowContext, onLog)
        }
    }

    fun execute(
        graph: WorkflowGraph,
        androidContext: Context,
        onLog: ((String) -> Unit)? = null,
    ): Job {
        val workflowContext = WorkflowContext(androidContext)
        return execute(graph, workflowContext, onLog)
    }

    private suspend fun executeSuspend(
        graph: WorkflowGraph,
        workflowContext: WorkflowContext,
        onLog: ((String) -> Unit)? = null,
    ) {
        workflowContext.onLog = onLog

        val validation = WorkflowGraphValidator.validate(graph, factory.registeredTypes())
        validation.warnings.forEach { issue ->
            val nodePrefix = issue.nodeId?.let { "node '$it': " }.orEmpty()
            workflowContext.log("⚠ Validation warning: $nodePrefix${issue.message}")
        }
        if (!validation.isValid) {
            validation.errors.forEach { issue ->
                val nodePrefix = issue.nodeId?.let { "node '$it': " }.orEmpty()
                workflowContext.log("✗ Validation error: $nodePrefix${issue.message}")
            }
            if (strictValidation) return
        }

        val nodes = graph.nodes.orEmpty()
        val startId = graph.startNodeId?.trim()
        if (nodes.isEmpty() || startId.isNullOrBlank()) {
            Log.w(TAG, "execute: invalid graph (nodes=${nodes.size}, startId=$startId)")
            workflowContext.log("Habitat: Invalid graph — missing nodes or start_node_id")
            return
        }

        val index = HashMap(nodes)
        Log.i(TAG, "=== Workflow start: ${nodes.size} nodes, start=$startId ===")
        workflowContext.log("Graph loaded: ${nodes.size} nodes, starting from '$startId'")

        var currentId: String? = startId
        var step = 0
        var errorCount = 0

        while (currentId != null && step < maxSteps) {
            coroutineContext.ensureActive()
            val executingId = currentId
            val node = index[executingId]
            if (node == null) {
                Log.w(TAG, "step $step: node '$executingId' not found in graph")
                workflowContext.putVariable("_last_error", true)
                workflowContext.putVariable("_last_error_msg", "Missing node '$executingId'")
                workflowContext.log("Habitat: Missing node '$executingId'")
                errorCount++
                break
            }

            val nodeType = node.type?.trim().orEmpty()
            val handler = factory.get(nodeType)
            if (handler == null) {
                errorCount++
                val message = "No handler registered for type '$nodeType' (node '$executingId')"
                Log.e(TAG, "step $step: $message")
                workflowContext.putVariable("_last_error", true)
                workflowContext.putVariable("_last_error_msg", message)
                workflowContext.log("✗ $message")
                break
            }

            Log.i(TAG, "step $step: [$executingId] $nodeType")
            val startTime = System.currentTimeMillis()

            try {
                workflowContext.putVariable("_last_error", false)
                workflowContext.putVariable("_last_error_msg", "")
                val nextId = handler.handle(node, workflowContext)?.trim()?.takeIf { it.isNotEmpty() }
                if (nextId != null && !index.containsKey(nextId)) {
                    throw IllegalStateException("Node '$executingId' returned missing next node '$nextId'")
                }
                currentId = nextId
            } catch (e: Exception) {
                errorCount++
                val message = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "step $step: [$executingId] $nodeType FAILED: $message", e)
                workflowContext.putVariable("_last_error", true)
                workflowContext.putVariable("_last_error_msg", message)
                workflowContext.log("✗ Error in '$nodeType' (node '$executingId'): $message")
                currentId = node.branches?.get("error")?.trim()?.takeIf { it.isNotEmpty() }
                if (currentId != null) {
                    workflowContext.log("Recovering through error branch → '$currentId'")
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 1000) {
                Log.i(TAG, "step $step: [$executingId] $nodeType took ${elapsed}ms (slow)")
            }

            step++
        }

        if (step >= maxSteps) {
            Log.w(TAG, "=== Aborted after $maxSteps steps ===")
            workflowContext.putVariable("_last_error", true)
            workflowContext.putVariable("_last_error_msg", "Step limit $maxSteps reached")
            workflowContext.log("Habitat: Aborted (step limit $maxSteps reached)")
        } else {
            Log.i(TAG, "=== Finished: $step steps, $errorCount errors ===")
            workflowContext.log("Habitat: Finished ($step steps, $errorCount errors)")
        }
    }
}
