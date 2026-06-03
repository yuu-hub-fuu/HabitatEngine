package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
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
 */
class NodeLLMHandler(private val llmService: ILLMService? = null) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params.orEmpty()
        val prompt = params["prompt"]?.toString()?.trim()
        require(!prompt.isNullOrEmpty()) { "ACTION_AI_CHAT requires non-empty prompt" }

        val outputVar = params["output_var"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "llm_result"
        val service = llmService ?: throw IllegalStateException("ACTION_AI_CHAT requires an ILLMService instance")
        if (!service.isReady()) {
            throw IllegalStateException("ACTION_AI_CHAT LLM service is not ready; load a model before execution")
        }

        val finalPrompt = context.interpolate(prompt)
        context.log("NodeLLMHandler: starting inference with prompt length ${finalPrompt.length}")

        val result = withContext(Dispatchers.IO) {
            service.chat(finalPrompt)
        }
        context.putVariable(outputVar, result)
        context.putVariable("llm_success", true)
        context.log("NodeLLMHandler: inference completed, result saved to $outputVar")
        return node.nextResult()
    }
}
