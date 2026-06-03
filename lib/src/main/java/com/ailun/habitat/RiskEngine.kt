package com.ailun.habitat

object RiskEngine {
    fun assess(node: WorkflowNode, context: WorkflowContext): RiskEvent? {
        return when (node.type) {
            NodeHandlerFactory.ACTION_SHELL -> assessShell(node, context)
            NodeHandlerFactory.ACTION_FILE_OPERATION -> assessFile(node, context)
            NodeHandlerFactory.ACTION_CALL_PHONE -> RiskEvent(
                RiskLevel.HIGH, "phone.call",
                "拨号属于真实副作用动作", requiresConfirmation = true
            )
            NodeHandlerFactory.ACTION_SHARE -> RiskEvent(
                RiskLevel.MEDIUM, "android.share",
                "分享可能外传数据", requiresConfirmation = true
            )
            else -> null
        }
    }

    private fun assessShell(node: WorkflowNode, context: WorkflowContext): RiskEvent {
        val raw = node.params?.get("command")?.toString().orEmpty()
        val command = try { context.interpolate(raw).lowercase() } catch (_: Exception) { raw.lowercase() }

        val criticalPatterns = listOf("rm -rf", "reboot", "format", "dd ", "pm uninstall", "settings put", "content delete")
        if (criticalPatterns.any { command.contains(it) }) {
            return RiskEvent(RiskLevel.CRITICAL, "shell.exec",
                "Shell 命令包含高危操作: $raw", requiresConfirmation = true)
        }
        return RiskEvent(RiskLevel.HIGH, "shell.exec",
            "Shell 执行默认需要确认", requiresConfirmation = true)
    }

    private fun assessFile(node: WorkflowNode, context: WorkflowContext): RiskEvent? {
        val action = node.params?.get("action")?.toString()?.lowercase()
        val rawPath = node.params?.get("path")?.toString().orEmpty()
        val path = try { context.interpolate(rawPath) } catch (_: Exception) { rawPath }

        if (action == "delete") {
            return RiskEvent(RiskLevel.CRITICAL, "file.delete",
                "文件删除属于不可逆动作: $path", requiresConfirmation = true)
        }
        if (action == "write") {
            return RiskEvent(RiskLevel.MEDIUM, "file.write",
                "文件写入会修改本地数据: $path", requiresConfirmation = false)
        }
        return null
    }
}
