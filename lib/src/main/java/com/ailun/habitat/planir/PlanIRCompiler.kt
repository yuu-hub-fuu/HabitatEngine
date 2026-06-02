package com.ailun.habitat.planir

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.capability.CapabilityMapping
import com.ailun.habitat.capability.RiskEngine
import com.ailun.habitat.capability.RiskLevel

/**
 * Compiles a validated [PlanIR] into an executable flat [WorkflowGraph].
 *
 * Compilation steps:
 * 1. Create WorkflowNodes from PlanSteps, mapping metadata to node fields
 * 2. Auto-insert ACTION_CONFIRM before high-risk steps
 * 3. Generate TryCatch error handling based on FailureRecoveryStrategy
 * 4. Inline post_conditions as CONDITION_SWITCH check nodes
 * 5. Wire next/branches with correct generated node IDs
 */
class PlanIRCompiler(
    private val riskEngine: RiskEngine = RiskEngine(),
    private val expressionEngine: ExpressionEngine = ExpressionEngine(),
) {
    private var confirmCounter = 0
    private var errorCounter = 0
    private var checkCounter = 0

    fun compile(plan: PlanIR): CompilationResult {
        val warnings = mutableListOf<String>()
        val insertedConfirmations = mutableListOf<String>()
        val generatedErrorHandlers = mutableListOf<String>()
        val nodeMapping = mutableMapOf<String, String>()

        val compiledNodes = mutableMapOf<String, WorkflowNode>()
        var firstNodeId: String? = null

        // Process steps in order, generating nodes
        val generatedIds = mutableListOf<String>()
        for ((index, step) in plan.steps.withIndex()) {
            val stepNodeIds = generateNodesForStep(
                step, index, plan, compiledNodes, warnings, insertedConfirmations,
                generatedErrorHandlers, nodeMapping,
            )
            generatedIds.addAll(stepNodeIds)
            if (index == 0 && stepNodeIds.isNotEmpty()) {
                firstNodeId = stepNodeIds.first()
            }
        }

        // Wire sequential next pointers
        for (i in 0 until generatedIds.size - 1) {
            val currentNode = compiledNodes[generatedIds[i]]
            if (currentNode?.next == null && currentNode?.branches == null) {
                currentNode?.next = generatedIds[i + 1]
            }
        }

        val graph = WorkflowGraph().apply {
            startNodeId = firstNodeId ?: plan.steps.firstOrNull()?.id
            nodes = compiledNodes.toMap()
            name = plan.task.description.take(100)
            description = "Compiled from PlanIR v${plan.version}"
            capabilities = plan.requiredCapabilities.toList()
            successCriteria = if (plan.postconditions.isNotEmpty()) {
                mapOf(
                    "conditions" to plan.postconditions.map { cond ->
                        mapOf("type" to cond.type, "expression" to cond.expression)
                    },
                )
            } else null
        }

        return CompilationResult(
            graph = graph,
            warnings = warnings,
            insertedConfirmations = insertedConfirmations,
            generatedErrorHandlers = generatedErrorHandlers,
            nodeMapping = nodeMapping,
        )
    }

    private fun generateNodesForStep(
        step: PlanStep, index: Int, plan: PlanIR,
        nodes: MutableMap<String, WorkflowNode>,
        warnings: MutableList<String>,
        insertedConfirmations: MutableList<String>,
        generatedErrorHandlers: MutableList<String>,
        nodeMapping: MutableMap<String, String>,
    ): List<String> {
        val generated = mutableListOf<String>()
        val stepId = step.id

        // 1. Pre-condition check node (if present)
        if (step.preCondition != null) {
            val checkId = "${stepId}_pre_check_${checkCounter++}"
            val condNode = WorkflowNode().apply {
                id = checkId; type = "CONDITION_SWITCH"; label = "Pre: ${step.intent.take(30)}"
                params = mapOf("expression" to step.preCondition.expression)
                branches = mapOf("true" to stepId, "false" to null)
            }
            nodes[checkId] = condNode
            generated.add(checkId)
        }

        // 2. Confirmation node (if high-risk)
        val risk = CapabilityMapping.riskLevelForNodeType(step.actionType)
        val needsConfirmation = step.requireConfirmation || risk.score >= RiskLevel.CONFIRMATION_THRESHOLD_SCORE
        if (needsConfirmation && step.actionType != "ACTION_CONFIRM") {
            val confirmId = "${stepId}_confirm_${confirmCounter++}"
            val confirmNode = WorkflowNode().apply {
                id = confirmId; type = "ACTION_CONFIRM"; label = "确认: ${step.intent.take(30)}"
                params = mapOf(
                    "message" to "即将执行: ${step.intent}",
                    "target_node" to stepId,
                )
                next = stepId
            }
            nodes[confirmId] = confirmNode
            insertedConfirmations.add(confirmId)
            generated.add(confirmId)
        }

        // 3. Main action node
        val baseParams = step.params.toMutableMap()
        if (step.outputVar != null) baseParams["output_var"] = step.outputVar
        if (step.timeoutMs != null) baseParams["timeout"] = step.timeoutMs

        val actionNode = WorkflowNode().apply {
            id = stepId; type = step.actionType; label = step.intent.take(50)
            description = step.intent
            params = baseParams
        }
        nodes[stepId] = actionNode
        nodeMapping[step.id] = stepId
        generated.add(stepId)

        // 4. Post-condition check node
        if (step.postCondition != null) {
            val postId = "${stepId}_post_check_${checkCounter++}"
            val postNode = WorkflowNode().apply {
                id = postId; type = "CONDITION_SWITCH"; label = "验证: ${step.intent.take(30)}"
                params = mapOf("expression" to step.postCondition.expression)
                branches = mapOf("true" to null, "false" to null)
            }
            nodes[postId] = postNode
            actionNode.next = postId
            generated.add(postId)
        }

        // 5. Error handling node (if not ABORT)
        if (step.onFailure != "ABORT") {
            val errorId = "${stepId}_on_error_${errorCounter++}"
            val errorNode = WorkflowNode().apply {
                id = errorId; type = "ACTION_TRY_CATCH"; label = "异常处理: ${step.intent.take(30)}"
                params = mapOf("catch_var" to "error_${stepId}")
                branches = mapOf(
                    "error" to when {
                        step.onFailure.startsWith("JUMP_TO(") ->
                            step.onFailure.removePrefix("JUMP_TO(").removeSuffix(")")
                        step.onFailure == "RETRY" -> stepId
                        else -> null
                    },
                    "success" to actionNode.next,
                )
            }
            nodes[errorId] = errorNode
            generatedErrorHandlers.add(errorId)
            generated.add(errorId)
        }

        return generated
    }
}
