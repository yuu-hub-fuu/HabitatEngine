package com.ailun.habitat

object DryRunEngine {
    fun inspect(graph: WorkflowGraph): GraphVerificationResult {
        val baseIssues = GraphVerifier.verify(graph).issues.toMutableList()
        val nodes = graph.nodes.orEmpty()

        for ((id, node) in nodes) {
            val risk = staticRisk(node)
            if (risk != null) {
                baseIssues += GraphIssue(GraphIssue.Level.WARNING, id, risk)
            }

            node.requiredCapabilities?.forEach { cap ->
                if (cap.isBlank()) {
                    baseIssues += GraphIssue(GraphIssue.Level.ERROR, id, "required_capabilities 包含空值")
                }
            }
        }

        return GraphVerificationResult(baseIssues)
    }

    private fun staticRisk(node: WorkflowNode): String? {
        return when (node.type) {
            NodeHandlerFactory.ACTION_SHELL -> "包含 Shell 执行节点，必须确认 capability"
            NodeHandlerFactory.ACTION_FILE_OPERATION -> {
                val action = node.params?.get("action")?.toString()?.lowercase()
                if (action == "delete") "包含文件删除节点，必须确认 capability" else null
            }
            NodeHandlerFactory.ACTION_HTTP_REQUEST -> "包含 HTTP 请求节点，注意外传变量"
            else -> null
        }
    }
}
