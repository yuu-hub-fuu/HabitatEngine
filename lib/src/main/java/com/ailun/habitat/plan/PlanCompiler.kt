package com.ailun.habitat.plan

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode

object PlanCompiler {
    fun compile(plan: PlanIR): WorkflowGraph {
        val graph = WorkflowGraph()
        graph.startNodeId = plan.steps.firstOrNull()?.id
        graph.name = plan.goal.take(100)
        graph.description = "Compiled from PlanIR"
        graph.capabilities = plan.requiredCapabilities.toList()
        graph.successCriteria = plan.successCriteria?.let {
            mapOf("conditions" to listOf(mapOf("type" to "VARIABLE_SATISFIES", "expression" to it)))
        }
        graph.nodes = plan.steps.associate { step ->
            step.id to WorkflowNode().apply {
                id = step.id
                type = step.actionType
                // Filter null values: Map<String, Any?> → Map<String, Any>
                params = step.params.filterValues { it != null }.mapValues { it.value!! }
                next = step.onSuccess
                branches = buildMap {
                    step.onFailure?.let { put("error", it) }
                }
                label = step.intent
                preCondition = step.preCondition
                postCondition = step.postCondition
                riskLevel = step.riskLevel
                requiredCapabilities = plan.requiredCapabilities
            }
        }
        graph.validate()
        return graph
    }
}
