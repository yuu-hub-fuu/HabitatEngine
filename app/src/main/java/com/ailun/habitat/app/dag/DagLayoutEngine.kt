package com.ailun.habitat.app.dag

object DagLayoutEngine {

    private const val NODE_WIDTH_DP = 180f
    private const val NODE_HEIGHT_DP = 56f
    private const val H_GAP_DP = 100f
    private const val V_GAP_DP = 36f

    fun layout(graph: DagGraph, density: Float): DagGraph {
        val nodes = graph.nodes.toMutableList()
        if (nodes.isEmpty()) return graph

        val nodeWidth = NODE_WIDTH_DP * density
        val nodeHeight = NODE_HEIGHT_DP * density
        val hGap = H_GAP_DP * density
        val vGap = V_GAP_DP * density

        // Build adjacency
        val children = mutableMapOf<String, MutableList<String>>()
        val parents = mutableMapOf<String, MutableList<String>>()
        for (node in nodes) {
            children[node.stepId] = mutableListOf()
            parents[node.stepId] = mutableListOf()
        }
        for (edge in graph.edges) {
            if (edge.type == DagEdgeType.BACK_EDGE) continue
            children[edge.fromNodeId]?.add(edge.toNodeId)
            parents[edge.toNodeId]?.add(edge.fromNodeId)
        }

        // Phase 1: Layer assignment (topological sort)
        val layers = mutableListOf<MutableList<DagNode>>()
        val assigned = mutableSetOf<String>()
        val inDegree = mutableMapOf<String, Int>()
        for (node in nodes) {
            val preds = parents[node.stepId]?.filter { it in nodes.map { n -> n.stepId } }?.size ?: 0
            inDegree[node.stepId] = preds
        }

        var currentLayer = nodes.filter { (inDegree[it.stepId] ?: 0) == 0 }.toMutableList()
        while (currentLayer.isNotEmpty()) {
            layers.add(currentLayer)
            assigned.addAll(currentLayer.map { it.stepId })
            val nextLayer = mutableListOf<DagNode>()
            for (node in currentLayer) {
                for (childId in children[node.stepId] ?: emptyList()) {
                    if (childId in assigned) continue
                    val newDegree = (inDegree[childId] ?: 1) - 1
                    inDegree[childId] = newDegree
                    if (newDegree == 0) {
                        nodes.find { it.stepId == childId }?.let { nextLayer.add(it) }
                    }
                }
            }
            currentLayer = nextLayer
        }

        // Phase 2: Barycenter crossing reduction (3 iterations)
        repeat(3) {
            for (li in 1 until layers.size) {
                val layer = layers[li]
                val prevLayerIds = layers[li - 1].map { it.stepId }.toSet()
                layer.sortBy { node ->
                    val preds = parents[node.stepId]?.filter { it in prevLayerIds } ?: emptyList()
                    if (preds.isEmpty()) 0.0
                    else {
                        val positions = preds.mapNotNull { pid ->
                            layers[li - 1].indexOfFirst { it.stepId == pid }.takeIf { it >= 0 }
                        }
                        if (positions.isEmpty()) 0.0
                        else positions.average()
                    }
                }
            }
        }

        // Phase 3: Coordinate assignment
        for ((li, layer) in layers.withIndex()) {
            val totalHeight = layer.size * nodeHeight + (layer.size - 1) * vGap
            val startY = -totalHeight / 2f
            for ((ni, node) in layer.withIndex()) {
                val idx = nodes.indexOfFirst { it.stepId == node.stepId }
                if (idx >= 0) {
                    nodes[idx] = nodes[idx].copy(
                        x = li * (nodeWidth + hGap),
                        y = startY + ni * (nodeHeight + vGap)
                    )
                }
            }
        }

        return graph.copy(nodes = nodes)
    }
}
