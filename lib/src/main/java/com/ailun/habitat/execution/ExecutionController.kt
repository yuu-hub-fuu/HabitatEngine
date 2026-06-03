package com.ailun.habitat.execution

import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.NodeHandlerFactory
import com.ailun.habitat.capability.RiskEngine
import com.ailun.habitat.capability.RiskLevel
import com.ailun.habitat.graph.GraphVerifier
import com.ailun.habitat.perception.PerceptionEngine

class ExecutionController(
    private val graphVerifier: GraphVerifier,
    private val riskEngine: RiskEngine = RiskEngine(),
    private val perceptionEngine: PerceptionEngine? = null,
) {
    fun dryRun(graph: WorkflowGraph): DryRunResult {
        val verification = graphVerifier.verify(graph)
        val risk = riskEngine.assessGraph(graph)

        val shellRisks = mutableListOf<ShellRisk>()
        val httpRisks = mutableListOf<HttpExfiltrationRisk>()
        val dangerousPaths = mutableListOf<String>()

        graph.nodes?.forEach { (_, node) ->
            if (node.type == NodeHandlerFactory.ACTION_SHELL) {
                val cmd = node.params?.get("command")?.toString() ?: return@forEach
                val destructive = listOf("rm ", "rm -rf", "reboot", "pm uninstall", "dd if=")
                    .any { cmd.contains(it, ignoreCase = true) }
                shellRisks.add(ShellRisk(node.id ?: "", cmd, cmd, destructive,
                    if (destructive) RiskLevel.CRITICAL else RiskLevel.HIGH))
            }
            if (node.type == NodeHandlerFactory.ACTION_HTTP_REQUEST) {
                val method = node.params?.get("method")?.toString()?.uppercase() ?: "GET"
                if (method in listOf("POST", "PUT", "PATCH")) {
                    val url = node.params?.get("url")?.toString() ?: ""
                    val body = node.params?.get("body")?.toString() ?: ""
                    val sendsVars = Regex("\\$\\{(\\w+)}").findAll(body).map { it.groupValues[1] }.toList()
                    httpRisks.add(HttpExfiltrationRisk(node.id ?: "", url, sendsVars,
                        if (sendsVars.isNotEmpty()) RiskLevel.MEDIUM else RiskLevel.LOW))
                }
            }
            if (node.type == NodeHandlerFactory.ACTION_FILE_OPERATION) {
                val path = node.params?.get("path")?.toString() ?: ""
                if (path.contains("..") || path.startsWith("/system") || path.startsWith("/data/data")) {
                    dangerousPaths.add("${node.id}: $path")
                }
            }
        }

        val permissionGaps = mutableListOf<String>()
        if (shellRisks.isNotEmpty()) permissionGaps.add("SHELL_EXEC required")
        val hasSmsRead = graph.nodes?.values?.any { it.type == NodeHandlerFactory.ACTION_READ_SMS } == true
        if (hasSmsRead) permissionGaps.add("READ_SMS required")

        return DryRunResult(
            verification = verification,
            shellRisks = shellRisks,
            httpExfiltrationRisks = httpRisks,
            dangerousFilePaths = dangerousPaths,
            permissionGaps = permissionGaps,
            overallRisk = risk.overallRisk,
            canProceedToShadow = verification.isValid && shellRisks.none { it.isDestructive },
        )
    }

    suspend fun shadowRun(graph: WorkflowGraph, context: WorkflowContext): ShadowRunResult {
        val steps = mutableListOf<ShadowStepResult>()
        val state = perceptionEngine?.capture()

        for ((_, node) in graph.nodes ?: emptyMap()) {
            val target = node.params?.get("target")?.toString()
                ?: node.params?.get("selector")?.toString()
                ?: node.params?.get("text")?.toString()
                ?: node.params?.get("desc")?.toString()
                ?: node.params?.get("id")?.toString()

            val match = if (state != null && !target.isNullOrBlank()) {
                perceptionEngine.findBestMatch(state, target)
            } else null

            val requiresTarget = node.type in setOf(
                NodeHandlerFactory.ACTION_CLICK,
                NodeHandlerFactory.ACTION_LONG_PRESS,
                NodeHandlerFactory.ACTION_INPUT_TEXT,
                NodeHandlerFactory.ACTION_FIND_ELEMENT,
            )
            val targetFound = when {
                target.isNullOrBlank() -> !requiresTarget
                state == null -> false
                else -> match != null
            }
            val confidence = when {
                target.isNullOrBlank() && !requiresTarget -> 1.0f
                match != null -> match.confidenceScore
                else -> 0.0f
            }
            val riskReasons = riskEngine.assessNode(node).riskReasons.toMutableList()
            if (state == null && requiresTarget) riskReasons += "PerceptionEngine unavailable; target cannot be verified"
            if (!targetFound && !target.isNullOrBlank()) riskReasons += "Target '$target' not found in current screen state"

            steps.add(ShadowStepResult(
                nodeId = node.id ?: "",
                nodeType = node.type ?: "",
                targetSelector = target,
                targetFound = targetFound,
                targetConfidence = confidence,
                wouldSucceed = targetFound,
                risks = riskReasons,
            ))
        }
        return ShadowRunResult(
            steps = steps,
            overallPredictedSuccess = steps.all { it.wouldSucceed },
            predictedFailures = steps.filter { !it.wouldSucceed }.map {
                PredictedFailure(it.nodeId, "Target check failed: ${it.targetSelector ?: "no selector"}", RiskLevel.LOW)
            },
        )
    }

    suspend fun sandboxRun(graph: WorkflowGraph, context: WorkflowContext): SandboxRunResult {
        val warnings = mutableListOf<String>()
        graph.nodes?.forEach { (id, node) ->
            when (node.type) {
                NodeHandlerFactory.ACTION_FILE_OPERATION -> {
                    val path = node.params?.get("path")?.toString() ?: ""
                    if (path.startsWith("/") && !path.startsWith("/data/local/tmp")) {
                        warnings.add("FILE_OPERATION[$id]: Would access $path (redirected to temp)")
                    }
                }
                NodeHandlerFactory.ACTION_SHELL -> {
                    warnings.add("SHELL[$id]: Command execution blocked in sandbox")
                }
                NodeHandlerFactory.ACTION_HTTP_REQUEST -> {
                    warnings.add("HTTP[$id]: Network request blocked in sandbox")
                }
            }
        }
        return SandboxRunResult(passed = warnings.isEmpty(), warnings = warnings)
    }
}

data class DryRunResult(
    val verification: com.ailun.habitat.graph.GraphVerifyResult,
    val shellRisks: List<ShellRisk> = emptyList(),
    val httpExfiltrationRisks: List<HttpExfiltrationRisk> = emptyList(),
    val dangerousFilePaths: List<String> = emptyList(),
    val permissionGaps: List<String> = emptyList(),
    val overallRisk: RiskLevel = RiskLevel.LOW,
    val canProceedToShadow: Boolean = false,
) {
    val summary: String get() = buildString {
        append("Dry-run: ${if (verification.isValid) "PASS" else "FAIL"}")
        append(" | Risk: ${overallRisk.label}")
        append(" | Shell risks: ${shellRisks.size}")
        append(" | HTTP risks: ${httpExfiltrationRisks.size}")
        append(" | Can proceed: $canProceedToShadow")
    }
}

data class ShellRisk(
    val nodeId: String, val command: String, val interpolatedExample: String,
    val isDestructive: Boolean, val riskLevel: RiskLevel,
)

data class HttpExfiltrationRisk(
    val nodeId: String, val url: String, val sendsVariables: List<String>, val riskLevel: RiskLevel,
)

data class ShadowRunResult(
    val steps: List<ShadowStepResult>, val overallPredictedSuccess: Boolean,
    val predictedFailures: List<PredictedFailure>,
)

data class ShadowStepResult(
    val nodeId: String, val nodeType: String, val targetSelector: String?,
    val targetFound: Boolean, val targetConfidence: Float, val wouldSucceed: Boolean,
    val risks: List<String>,
)

data class PredictedFailure(val nodeId: String, val reason: String, val severity: RiskLevel)

data class SandboxRunResult(val passed: Boolean, val warnings: List<String>)
