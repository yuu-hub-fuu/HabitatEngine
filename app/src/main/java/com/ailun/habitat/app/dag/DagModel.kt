package com.ailun.habitat.app.dag

enum class DagEdgeType {
    SEQUENTIAL,
    BRANCH_TRUE,
    BRANCH_FALSE,
    BACK_EDGE,
    EXIT_EDGE,
    JUMP,
    TRIGGER
}

data class DagNode(
    val stepId: String,
    val stepIndex: Int,
    val moduleId: String,
    val label: String,
    val moduleName: String,
    val categoryId: String,
    val isTrigger: Boolean = false,
    var x: Float = 0f,
    var y: Float = 0f
)

data class DagEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val type: DagEdgeType,
    val label: String = ""
)

data class DagGraph(
    val nodes: List<DagNode>,
    val edges: List<DagEdge>
)
