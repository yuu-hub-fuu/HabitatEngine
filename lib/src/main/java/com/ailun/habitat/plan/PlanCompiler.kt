package com.ailun.habitat.plan

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode

object PlanCompiler {
    fun compile(plan: PlanIR): WorkflowGraph {
        val graph = WorkflowGraph()
        graph.startNodeId = plan.steps.firstOrNull()?.id
        graph.nodes = plan.steps.associate { step ->
            step.id to WorkflowNode().apply {
                id = step.id
                type = step.actionType
                params = step.params
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
