package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.ai.ILLMService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ACTION_AI_CHAT]：调用端侧大模型进行推理。
 *
 * params:
 * - `prompt`: 提示词，支持 ${var} 变量插值。
 * - `output_var`: 输出变量名，默认 "llm_result"。
 * - `system_prompt`: 可选系统提示词。
 */
class NodeLLMHandler(private val llmService: ILLMService? = null) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params.orEmpty()
        val prompt = params["prompt"]?.toString()?.trim()
        if (prompt.isNullOrEmpty()) {
            context.log("ACTION_AI_CHAT: missing prompt")
            return NodeResult.failure(node.next, "Missing 'prompt' parameter",
                mapOf("llm_success" to false))
        }

        val outputVar = params["output_var"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "llm_result"

        val service = llmService
        if (service == null) {
            context.log("ACTION_AI_CHAT: no ILLMService instance available")
            return NodeResult.failure(node.next, "ILLMService not available — load a model before execution",
                mapOf("llm_success" to false))
        }

        if (!service.isReady()) {
            context.log("ACTION_AI_CHAT: LLM service is not ready")
            return NodeResult.failure(node.next, "LLM service is not ready — load a model before execution",
                mapOf("llm_success" to false))
        }

        val finalPrompt = context.interpolate(prompt)
        val systemPrompt = params["system_prompt"]?.toString()?.let { context.interpolate(it) }
        context.log("NodeLLMHandler: starting inference with prompt length ${finalPrompt.length}")

        return try {
            val result = withContext(Dispatchers.IO) {
                service.chat(finalPrompt)
            }
            context.log("NodeLLMHandler: inference completed, result saved to $outputVar")
            NodeResult.success(node.next, mapOf(outputVar to result, "llm_success" to true))
        } catch (e: Exception) {
            context.log("NodeLLMHandler: inference failed: ${e.message}")
            NodeResult.failure(node.next, "LLM inference failed: ${e.message}",
                mapOf("llm_success" to false, "llm_error" to (e.message ?: "Unknown")))
        }
    }
}
