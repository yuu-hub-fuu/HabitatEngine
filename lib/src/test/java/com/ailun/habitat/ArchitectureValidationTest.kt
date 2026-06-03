package com.ailun.habitat

import com.ailun.habitat.plan.PlanCompiler
import com.ailun.habitat.plan.PlanIR
import com.ailun.habitat.plan.PlanStepIR
import org.junit.Assert.*
import org.junit.Test

class ArchitectureValidationTest {

    // ── Test 1: GraphVerifier detects dangling next ──

    @Test
    fun graphVerifier_detectsDanglingNext() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", "ACTION_LOG", next = "missing"),
            ),
        )
        val result = GraphVerifier.verify(graph)
        assertTrue("Should have errors for dangling next", result.hasError)
        assertTrue(result.issues.any { it.message.contains("next 指向不存在的节点") })
    }

    // ── Test 2: GraphVerifier detects dangling branch ──

    @Test
    fun graphVerifier_detectsDanglingBranch() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", "CONDITION_SWITCH",
                    branches = mapOf("true" to "real_target", "false" to "missing")),
                "real_target" to node("real_target", "ACTION_LOG"),
            ),
        )
        val result = GraphVerifier.verify(graph)
        assertTrue("Should have errors for dangling branch", result.hasError)
        assertTrue(result.issues.any { it.message.contains("branch 'false'") })
    }

    // ── Test 3: GraphVerifier detects unreachable nodes ──

    @Test
    fun graphVerifier_detectsUnreachableNode() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", "ACTION_LOG"),
                "orphan" to node("orphan", "ACTION_LOG"),
            ),
        )
        val result = GraphVerifier.verify(graph)
        assertFalse("Orphan node should only be a warning", result.hasError)
        assertTrue(result.issues.any { it.nodeId == "orphan" && it.message.contains("不可达") })
    }

    // ── Test 4: GraphVerifier catches missing id and type ──

    @Test
    fun graphVerifier_missingIdAndType() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to WorkflowNode().apply { id = null; type = null },
            ),
        )
        val result = GraphVerifier.verify(graph)
        assertTrue(result.hasError)
    }

    // ── Test 5: DryRunEngine static risk ──

    @Test
    fun dryRunEngine_warnsOnShellNode() {
        val graph = graphOf(
            start = "s1",
            nodes = mapOf(
                "s1" to node("s1", "ACTION_SHELL", next = null),
            ),
        )
        val result = DryRunEngine.inspect(graph)
        val shellWarnings = result.issues.filter {
            it.level == GraphIssue.Level.WARNING && it.message.contains("Shell")
        }
        assertTrue("Should warn about Shell node", shellWarnings.isNotEmpty())
    }

    // ── Test 6: PlanCompiler produces valid graph ──

    @Test
    fun planCompiler_producesValidGraph() {
        val plan = PlanIR(
            goal = "test",
            steps = listOf(
                PlanStepIR(
                    id = "s1", intent = "step1", actionType = "ACTION_LOG",
                    params = mapOf("message" to "hello"),
                    onSuccess = "s2",
                ),
                PlanStepIR(
                    id = "s2", intent = "step2", actionType = "ACTION_TOAST",
                ),
            ),
        )
        val graph = PlanCompiler.compile(plan)
        assertEquals("s1", graph.startNodeId)
        assertEquals(2, graph.nodes?.size)
    }

    // ── Test 7: WorkflowGraph.validate() throws on bad graph ──

    @Test(expected = IllegalArgumentException::class)
    fun validate_throwsOnDanglingEdge() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", "ACTION_LOG", next = "missing"),
            ),
        )
        graph.validate()
    }

    // ── Helpers ──

    private fun graphOf(start: String, nodes: Map<String, WorkflowNode>): WorkflowGraph =
        WorkflowGraph().apply {
            startNodeId = start
            this.nodes = nodes
        }

    private fun node(id: String, type: String, next: String? = null, branches: Map<String, String?>? = null): WorkflowNode =
        WorkflowNode().apply {
            this.id = id
            this.type = type
            this.next = next
            this.branches = branches
        }
}
