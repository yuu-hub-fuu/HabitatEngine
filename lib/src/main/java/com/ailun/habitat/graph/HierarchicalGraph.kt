package com.ailun.habitat.graph

import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.planir.Condition

data class HierarchicalGraph(
    val name: String = "",
    val description: String = "",
    val subGraphs: Map<String, SubGraphRef> = emptyMap(),
    val skills: Map<String, SkillRef> = emptyMap(),
    val macros: Map<String, MacroRef> = emptyMap(),
    val guards: Map<String, GuardDef> = emptyMap(),
    val nodes: Map<String, HierarchicalNode> = emptyMap(),
    val startNodeId: String = "",
    val successCriteria: List<Condition> = emptyList(),
    val requiredCapabilities: List<String> = emptyList(),
)

data class SubGraphRef(
    val id: String,
    val description: String = "",
    val graph: WorkflowGraph,
    val inputMapping: Map<String, String> = emptyMap(),
    val outputMapping: Map<String, String> = emptyMap(),
)

data class SkillRef(
    val skillId: String,
    val paramOverrides: Map<String, Any> = emptyMap(),
)

data class MacroRef(
    val templateId: String,
    val paramOverrides: Map<String, String> = emptyMap(),
)

data class GuardDef(
    val id: String,
    val expression: String,
    val description: String = "",
    val onViolation: String = "",
)

data class HierarchicalNode(
    val id: String,
    val type: String,  // "node" | "subgraph" | "skill" | "macro" | "guard"
    val refId: String? = null,  // ID of the subgraph/skill/macro this references
    val params: Map<String, Any>? = null,
    val label: String? = null,
    val description: String? = null,
    val next: String? = null,
    val branches: Map<String, String?>? = null,
)
