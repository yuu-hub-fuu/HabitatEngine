package com.ailun.habitat.graph

import com.ailun.habitat.NodeHandlerFactory
import com.ailun.habitat.WorkflowGraph
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.capability.RiskEngine
import com.ailun.habitat.expression.ExpressionEngine

/**
 * Comprehensive static analysis engine for Habitat workflow graphs.
 *
 * Replaces the minimal [WorkflowGraph.validate()] with full static analysis:
 * 1. Edge validity — all next/branches references exist
 * 2. Reachability — all nodes reachable from start_node_id (BFS)
 * 3. Cycle detection — forward-edge-only DFS to find dead loops
 * 4. Variable analysis — topo-sort to find dereferences of unset variables
 * 5. Expression validation — parse all expressions via ExpressionEngine
 * 6. Risk analysis — flag high-risk nodes without guards
 * 7. Branch coverage — check every branches key has a path
 */
class GraphVerifier(
    private val expressionEngine: ExpressionEngine,
    private val riskEngine: RiskEngine,
    private val factory: NodeHandlerFactory? = null,  // Null = skip handler registration check
) {

    /**
     * Full verification of a workflow graph.
     * Returns a structured result with errors (must-fix), warnings (should-fix),
     * and detailed structural analysis.
     */
    fun verify(graph: WorkflowGraph): GraphVerifyResult {
        val nodes = graph.nodes ?: emptyMap()
        val startId = graph.startNodeId
        val errors = mutableListOf<VerifyError>()
        val warnings = mutableListOf<VerifyWarning>()

        // ── 0. Structural basics ──
        if (nodes.isEmpty()) {
            return GraphVerifyResult(
                isValid = false,
                errors = listOf(VerifyError.EmptyNodes),
            )
        }

        if (startId == null || startId !in nodes) {
            errors.add(VerifyError.MissingStartNode(startId ?: "null"))
            return GraphVerifyResult(
                isValid = false,
                errors = errors,
            )
        }

        // ── 1. Edge validity ──
        val nodeIds = nodes.keys
        val danglingEdges = mutableListOf<DanglingEdge>()
        for ((id, node) in nodes) {
            // Check next pointer
            val next = node.next
            if (next != null && next !in nodeIds) {
                danglingEdges.add(DanglingEdge(id, next, "next"))
                errors.add(VerifyError.DanglingBranch(id, "next", next))
            }

            // Check branches
            node.branches?.forEach { (branchName, targetId) ->
                if (targetId != null && targetId !in nodeIds) {
                    danglingEdges.add(DanglingEdge(id, targetId, "branches.$branchName"))
                    errors.add(VerifyError.DanglingBranch(id, "branches.$branchName", targetId))
                }
            }
        }

        // ── 2. Reachability (BFS from start) ──
        val reachable = bfs(nodes, startId)
        val unreachableNodes = nodeIds - reachable
        for (unreachable in unreachableNodes) {
            warnings.add(VerifyWarning.UnusedNode(unreachable))
        }

        // ── 3. Cycle detection ──
        // Use DFS on the "next" and "branches.*" forward edges only.
        // A loop without a CONDITION_SWITCH break condition is a dead loop.
        val deadLoops = mutableListOf<DeadLoop>()
        val visited = mutableSetOf<String>()
        val recStack = mutableMapOf<String, Boolean>()

        fun detectCycles(currentId: String, path: MutableList<String>) {
            if (currentId in recStack) {
                val cycleStart = recStack.entries.firstOrNull { it.key == currentId }
                if (cycleStart != null) {
                    val cycleNodes = path.drop(path.indexOf(currentId))
                    val hasBreak = cycleNodes.any { nodeId ->
                        val n = nodes[nodeId]
                        n?.type in listOf("CONDITION_SWITCH", "CONDITION_ADVANCED_SWITCH") &&
                        n?.branches?.size == 2
                    }
                    deadLoops.add(DeadLoop(currentId, cycleNodes, hasBreak))
                    if (!hasBreak) {
                        errors.add(VerifyError.CircularDependency(cycleNodes))
                    } else {
                        warnings.add(VerifyWarning.CircularRisk(cycleNodes))
                    }
                }
                return
            }

            val node = nodes[currentId] ?: return
            visited.add(currentId)
            recStack[currentId] = true
            path.add(currentId)

            // Follow next
            node.next?.let { detectCycles(it, path) }

            // Follow branches
            node.branches?.values?.filterNotNull()?.forEach { targetId ->
                detectCycles(targetId, path)
            }

            recStack.remove(currentId)
            path.removeAt(path.lastIndex)
        }

        // Only detect cycles from reachable nodes
        for (id in reachable) {
            if (id !in visited) {
                detectCycles(id, mutableListOf())
            }
        }

        // ── 4. Variable analysis ──
        // Build an approximate topological order from start
        val topoOrder = topologicalSort(nodes, startId, reachable)
        val definedVars = mutableSetOf<String>()
        val undefinedVariables = mutableMapOf<String, MutableList<String>>()
        val nullVariableRisks = mutableMapOf<String, MutableList<String>>()

        // Nodes that define variables
        val varDefiners = mapOf(
            "ACTION_SET_VARIABLE" to { node: WorkflowNode ->
                listOfNotNull(
                    node.params?.get("key")?.toString(),
                    node.params?.get("name")?.toString(),
                )
            },
            "ACTION_CLIPBOARD" to { _: WorkflowNode -> listOf("clipboard_content") },
            "CONDITION_SWITCH" to { _: WorkflowNode -> listOf("switch_result") },
            // Add more as needed from handler analysis
        )

        for (nodeId in topoOrder) {
            val node = nodes[nodeId] ?: continue

            // Record variables defined by this node
            val defined = varDefiners[node.type]?.invoke(node) ?: emptyList()
            defined.forEach { definedVars.add(it) }

            // Also record the standard output variables
            node.params?.get("output_var")?.toString()?.let { definedVars.add(it) }

            // Check all variable references in node params
            val allParamText = node.params?.values
                ?.filterIsInstance<String>()
                ?.joinToString(" ") ?: ""

            // Find ${var} patterns
            val varRefs = Regex("""\$\{(\w+)}""").findAll(allParamText)
            for (match in varRefs) {
                val varName = match.groupValues[1]
                if (varName !in definedVars) {
                    undefinedVariables.getOrPut(varName) { mutableListOf() }.add(nodeId)
                }
            }

            // Check expression params
            val exprText = node.params?.get("expression")?.toString()
                ?: node.params?.get("condition_expr")?.toString()
            if (exprText != null) {
                try {
                    val ast = expressionEngine.parse(exprText)
                    val astRefs = ast.referencedVariables()
                    for (varName in astRefs) {
                        if (varName !in definedVars && varName !in listOf("is_daytime", "is_nighttime")) {
                            nullVariableRisks.getOrPut(varName) { mutableListOf() }.add(nodeId)
                        }
                    }
                } catch (_: Exception) {
                    // Expression parse failure — already caught by expression validation below
                }
            }
        }

        // Convert to immutable
        val undefinedVarsImmutable = undefinedVariables.mapValues { it.value.toList() }
        val nullVarRisksImmutable = nullVariableRisks.mapValues { it.value.toList() }

        for ((varName, nodeIds) in undefinedVarsImmutable) {
            for (nid in nodeIds) {
                warnings.add(VerifyWarning.NullVariableRisk(nid, varName, "variable may not be defined before use"))
            }
        }

        // ── 5. Expression validation ──
        for ((id, node) in nodes) {
            if (id !in reachable) continue

            val exprText = node.params?.get("expression")?.toString()
                ?: node.params?.get("condition_expr")?.toString()
            if (exprText != null && exprText.isNotBlank()) {
                try {
                    expressionEngine.parse(exprText)
                } catch (e: Exception) {
                    errors.add(VerifyError.InvalidExpression(id, exprText, e.message ?: "parse error"))
                }
            }
        }

        // ── 6. Risk analysis ──
        val riskAssessment = riskEngine.assessGraph(graph)
        val highRiskWithoutGuards = mutableListOf<String>()

        // Build reverse-edge map to check for upstream ACTION_CONFIRM guards.
        val predecessors = mutableMapOf<String, MutableSet<String>>()
        for ((id, node) in nodes) {
            node.next?.let { predecessors.getOrPut(it) { mutableSetOf() }.add(id) }
            node.branches?.values?.filterNotNull()?.forEach { target ->
                predecessors.getOrPut(target) { mutableSetOf() }.add(id)
            }
        }

        for ((nodeId, assessment) in riskAssessment.nodeAssessments) {
            if (assessment.requiresConfirmation) {
                // Skip warning if any immediate predecessor is ACTION_CONFIRM.
                val guarded = predecessors[nodeId]?.any { predId ->
                    nodes[predId]?.type == NodeHandlerFactory.ACTION_CONFIRM
                } ?: false
                if (!guarded) {
                    highRiskWithoutGuards.add(nodeId)
                    warnings.add(VerifyWarning.HighRiskWithoutGuard(nodeId))
                }
            }
        }

        // ── 7. Branch coverage ──
        var totalBranches = 0
        var coveredBranches = 0
        val uncovered = mutableListOf<BranchCoverageReport.UncoveredBranch>()

        for ((id, node) in nodes) {
            if (id !in reachable) continue
            node.branches?.forEach { (branchName, targetId) ->
                totalBranches++
                if (targetId == null) {
                    uncovered.add(BranchCoverageReport.UncoveredBranch(id, branchName, "null target"))
                } else if (targetId !in reachable) {
                    uncovered.add(BranchCoverageReport.UncoveredBranch(id, branchName, "target '$targetId' unreachable"))
                } else {
                    coveredBranches++
                }
            }
        }

        val branchReport = if (totalBranches > 0) {
            BranchCoverageReport(totalBranches, coveredBranches, uncovered)
        } else null

        // ── 8. Handler registration check ──
        if (factory != null) {
            for ((id, node) in nodes) {
                if (id !in reachable) continue
                val type = node.type ?: continue
                if (factory.get(type) == null) {
                    errors.add(VerifyError.MissingHandler(id, type))
                }
            }
        }

        // ── 9. Success criteria ──
        val missingSuccessCriteria = graph.successCriteria == null && graph.nodes?.values?.none {
            it.postCondition != null
        } ?: true

        if (missingSuccessCriteria) {
            warnings.add(VerifyWarning.MissingSuccessCriteria)
        }

        val finalErrors = errors.toList()
        val isValid = finalErrors.isEmpty()

        return GraphVerifyResult(
            isValid = isValid,
            errors = finalErrors,
            warnings = warnings.toList(),
            unreachableNodes = unreachableNodes,
            danglingEdges = danglingEdges.toList(),
            deadLoops = deadLoops.toList(),
            undefinedVariables = undefinedVarsImmutable,
            nullVariableRisks = nullVarRisksImmutable,
            missingSuccessCriteria = missingSuccessCriteria,
            highRiskNodesWithoutGuards = highRiskWithoutGuards.toList(),
            branchCoverageReport = branchReport,
        )
    }

    // ──────── BFS from start node ────────

    private fun bfs(nodes: Map<String, WorkflowNode>, startId: String): Set<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current in visited) continue
            visited.add(current)

            val node = nodes[current] ?: continue
            node.next?.let { if (it !in visited) queue.add(it) }
            node.branches?.values?.filterNotNull()?.forEach { targetId ->
                if (targetId !in visited) queue.add(targetId)
            }
        }

        return visited
    }

    // ──────── Topological sort (approximate, best-effort) ────────

    private fun topologicalSort(
        nodes: Map<String, WorkflowNode>,
        startId: String,
        reachable: Set<String>,
    ): List<String> {
        val order = mutableListOf<String>()
        val visited = mutableSetOf<String>()

        fun dfs(currentId: String) {
            if (currentId !in reachable || currentId in visited) return
            visited.add(currentId)

            val node = nodes[currentId] ?: return

            // Follow branches before next (branches explore forward)
            node.branches?.values?.filterNotNull()?.forEach { targetId ->
                // Don't follow backwards edges (they go to already-visited nodes)
                if (targetId !in visited) {
                    dfs(targetId)
                }
            }

            node.next?.let { dfs(it) }

            order.add(currentId)
        }

        dfs(startId)

        // Add any remaining reachable nodes
        for (id in reachable) {
            if (id !in visited) dfs(id)
        }

        return order.reversed() // DFS post-order reversed = topological
    }
}
