package com.ailun.habitat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一的工作流执行服务 — 悬浮窗和应用界面通过此单例触发任务，
 * 确保互斥执行、状态一致。
 */
object HabitatExecutionService {

    private const val TAG = "HabitatExecutionSvc"

    private val execScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val startLock = Any()

    /** 检查指定工作流是否正在运行 */
    fun isRunning(workflowId: String): Boolean = activeJobs[workflowId]?.isActive == true

    /** 所有正在运行的工作流 ID */
    fun activeWorkflowIds(): Set<String> = activeJobs.keys.toSet()

    /**
     * 启动工作流执行。
     * @return [StartResult.Started] 如果成功启动，[StartResult.AlreadyRunning] 或 [StartResult.InvalidGraph] 否则。
     */
    fun start(
        workflowId: String,
        jsonContent: String,
        androidContext: Context,
        factory: NodeHandlerFactory,
        initialVars: Map<String, Any>? = null,
        onComplete: (() -> Unit)? = null,
        onLog: ((String) -> Unit)? = null,
    ): StartResult = synchronized(startLock) {
        if (isRunning(workflowId)) {
            Log.w(TAG, "Workflow '$workflowId' already running — skip duplicate start")
            onLog?.invoke("Workflow '$workflowId' already running; duplicate start skipped")
            return@synchronized StartResult.AlreadyRunning(workflowId)
        }

        // Parse and validate synchronously so callers get InvalidGraph before the coroutine.
        val graph: WorkflowGraph
        try {
            graph = HabitatJson.fromJson(jsonContent)
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            onLog?.invoke("Failed to parse workflow JSON: $reason")
            return@synchronized StartResult.InvalidGraph(workflowId, reason)
        }

        HabitatStateStore.setRunning(workflowId, true)

        val job = execScope.launch(start = CoroutineStart.LAZY) {
            try {
                Log.i(TAG, "=== Start workflow '$workflowId' ===")
                val executor = HabitatExecutor(factory)
                val ctx = WorkflowContext(definitionId = workflowId, context = androidContext)
                initialVars?.forEach { (k, v) -> ctx.variables[k] = v }
                executor.execute(graph, ctx, onLog).join()
            } catch (_: CancellationException) {
                Log.i(TAG, "Workflow '$workflowId' cancelled")
                onLog?.invoke("Workflow '$workflowId' cancelled")
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Workflow '$workflowId' error: $message", e)
                onLog?.invoke("Workflow '$workflowId' failed: $message")
            } finally {
                Log.i(TAG, "=== End workflow '$workflowId' ===")
                HabitatStateStore.setRunning(workflowId, false)
                activeJobs.remove(workflowId)
                onComplete?.invoke()
            }
        }

        activeJobs[workflowId] = job
        job.start()
        StartResult.Started(workflowId)
    }

    /** 停止工作流执行 */
    fun stop(workflowId: String) {
        val job = activeJobs.remove(workflowId)
        job?.cancel()
        HabitatStateStore.setRunning(workflowId, false)
        Log.i(TAG, "Workflow '$workflowId' stopped")
    }
}

/**
 * Structured result type for [HabitatExecutionService.start].
 *
 * Callers should match on the sealed class rather than treating it as a Boolean.
 */
sealed class StartResult(val workflowId: String) {
    /** Workflow was accepted and execution has begun. */
    data class Started(val id: String) : StartResult(id)

    /** A run with this ID is already in progress. */
    data class AlreadyRunning(val id: String) : StartResult(id)

    /** The JSON payload could not be parsed into a valid graph. */
    data class InvalidGraph(val id: String, val reason: String? = null) : StartResult(id)
}

/** Backward-compatible Boolean conversion for callers not yet migrated. */
fun StartResult.isStarted(): Boolean = this is StartResult.Started
