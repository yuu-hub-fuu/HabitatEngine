package com.ailun.habitat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowGraphValidatorTest {

    @Test
    fun validGraphPassesValidation() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", NodeHandlerFactory.ACTION_LOG, next = "done"),
                "done" to node("done", NodeHandlerFactory.ACTION_TOAST),
            ),
        )

        val result = WorkflowGraphValidator.validate(
            graph,
            registeredTypes = setOf(NodeHandlerFactory.ACTION_LOG, NodeHandlerFactory.ACTION_TOAST),
        )

        assertTrue(result.isValid)
    }

    @Test
    fun missingNextTargetFailsValidation() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", NodeHandlerFactory.ACTION_LOG, next = "missing"),
            ),
        )

        val result = WorkflowGraphValidator.validate(
            graph,
            registeredTypes = setOf(NodeHandlerFactory.ACTION_LOG),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("missing") })
    }

    @Test
    fun unregisteredTypeFailsValidation() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", "ACTION_FAKE"),
            ),
        )

        val result = WorkflowGraphValidator.validate(
            graph,
            registeredTypes = setOf(NodeHandlerFactory.ACTION_LOG),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.message.contains("unregistered") })
    }

    @Test
    fun unreachableNodeIsWarningOnly() {
        val graph = graphOf(
            start = "start",
            nodes = mapOf(
                "start" to node("start", NodeHandlerFactory.ACTION_LOG),
                "orphan" to node("orphan", NodeHandlerFactory.ACTION_LOG),
            ),
        )

        val result = WorkflowGraphValidator.validate(
            graph,
            registeredTypes = setOf(NodeHandlerFactory.ACTION_LOG),
        )

        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.nodeId == "orphan" })
    }

    private fun graphOf(start: String, nodes: Map<String, WorkflowNode>): WorkflowGraph = WorkflowGraph().apply {
        startNodeId = start
        this.nodes = nodes
    }

    private fun node(
        id: String,
        type: String,
        next: String? = null,
        branches: Map<String, String?>? = null,
    ): WorkflowNode = WorkflowNode().apply {
        this.id = id
        this.type = type
        this.next = next
        this.branches = branches
    }
}
