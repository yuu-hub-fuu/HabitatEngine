package com.ailun.habitat.app

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.app.dag.DagEdge
import com.ailun.habitat.app.dag.DagEdgeType
import com.ailun.habitat.app.dag.DagGraph
import com.ailun.habitat.app.dag.DagNode

object HabitatDagConverter {

    fun convert(graph: WorkflowGraph): DagGraph {
        val nodeMap = graph.nodes ?: return DagGraph(emptyList(), emptyList())
        val startId = graph.startNodeId

        val nodes = mutableListOf<DagNode>()
        val edges = mutableListOf<DagEdge>()

        // Assign stable indices
        val idToIndex = mutableMapOf<String, Int>()
        var idx = 0
        for ((nodeId, _) in nodeMap) {
            idToIndex[nodeId] = idx
            idx++
        }

        // Create DagNodes
        for ((nodeId, wn) in nodeMap) {
            val type = wn.type ?: "UNKNOWN"
            val isStart = nodeId == startId
            nodes.add(
                DagNode(
                    stepId = nodeId,
                    stepIndex = idToIndex[nodeId] ?: 0,
                    moduleId = type,
                    label = wn.label ?: wn.description ?: type,
                    moduleName = typeToDisplayName(type),
                    categoryId = typeToCategory(type),
                    isTrigger = isStart
                )
            )
        }

        // Create edges from next/branches
        for ((nodeId, wn) in nodeMap) {
            // Sequential edge via next
            if (!wn.next.isNullOrBlank() && wn.next in nodeMap) {
                edges.add(
                    DagEdge(
                        fromNodeId = nodeId,
                        toNodeId = wn.next!!,
                        type = DagEdgeType.SEQUENTIAL
                    )
                )
            }

            // Branch edges
            wn.branches?.forEach { (branchName, targetId) ->
                if (!targetId.isNullOrBlank() && targetId in nodeMap) {
                    val edgeType = when (branchName.lowercase()) {
                        "true", "yes", "success" -> DagEdgeType.BRANCH_TRUE
                        "false", "no", "failure" -> DagEdgeType.BRANCH_FALSE
                        else -> DagEdgeType.SEQUENTIAL
                    }
                    edges.add(
                        DagEdge(
                            fromNodeId = nodeId,
                            toNodeId = targetId,
                            type = edgeType,
                            label = branchName
                        )
                    )
                }
            }
        }

        return DagGraph(nodes, edges)
    }

    private fun typeToDisplayName(type: String): String = when {
        type.startsWith("CONDITION_") -> type.removePrefix("CONDITION_").lowercase()
            .replace("_", " ").replaceFirstChar { it.uppercase() }
        type.startsWith("ACTION_") -> type.removePrefix("ACTION_").lowercase()
            .replace("_", " ").replaceFirstChar { it.uppercase() }
        else -> type
    }

    private fun typeToCategory(type: String): String = when {
        type.contains("TOAST") || type.contains("VIBRATE") -> "interaction"
        type.startsWith("CONDITION_") -> "logic"
        type.contains("DELAY") || type.contains("LOOP") || type.contains("TRY_CATCH") -> "logic"
        type.contains("CLICK") || type.contains("SWIPE") || type.contains("INPUT_TEXT") -> "interaction"
        type.contains("GLOBAL_KEY") || type.contains("KEY_EVENT") -> "interaction"
        type.contains("SET_VARIABLE") || type.contains("MATH") || type.contains("CLIPBOARD") -> "data"
        type.contains("PARSE_JSON") || type.contains("PARSE_XML") -> "data"
        type.contains("HTTP") -> "network"
        type.contains("SHELL") || type.contains("LAUNCH_APP") || type.contains("WIFI") -> "device"
        type.contains("SCREENSHOT") || type.contains("READ_SCREEN") || type.contains("READ_SMS") -> "data"
        type.contains("AI_CHAT") || type.contains("LLM") -> "feishu"
        type.contains("FILE") -> "file"
        type.contains("LOG") || type.contains("TOAST") -> "interaction"
        else -> "core"
    }
}
