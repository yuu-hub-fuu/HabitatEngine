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

        val nodes = graph.nodes
        val startId = graph.startNodeId
        if (nodes.isNullOrEmpty() || startId.isNullOrBlank()) {
            Log.w(TAG, "execute: invalid graph (nodes=${nodes?.size}, startId=$startId)")
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
            val node = index[currentId]
            if (node == null) {
                Log.w(TAG, "step $step: node '$currentId' not found in graph")
                workflowContext.log("Habitat: Missing node '$currentId'")
                break
            }

            val nodeType = node.type ?: "<null type>"
            val handler = factory.get(nodeType)

            if (handler == null) {
                Log.w(TAG, "step $step: node '$currentId' type '$nodeType' — no handler registered, skipping to next=${node.next}")
                workflowContext.log("⚠ No handler for type '$nodeType' (node '$currentId'), skipping")
                currentId = node.next
                step++
                continue
            }

            Log.i(TAG, "step $step: [$currentId] $nodeType")
            val startTime = System.currentTimeMillis()

            try {
                currentId = handler.handle(node, workflowContext)
            } catch (e: Exception) {
                errorCount++
                Log.e(TAG, "step $step: [$currentId] $nodeType FAILED: ${e.message}", e)
                workflowContext.log("✗ Error in '$nodeType': ${e.message}")
                currentId = null
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 1000) {
                Log.i(TAG, "step $step: [$currentId] $nodeType took ${elapsed}ms (slow)")
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
    }
}
