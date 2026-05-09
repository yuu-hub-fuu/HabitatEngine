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

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val params = node.params ?: return node.next
        
        val prompt = params["prompt"]?.toString()?.trim()
        if (prompt.isNullOrEmpty()) {
            context.log("NodeLLMHandler: prompt is empty, skipping")
            return node.next
        }
        
        val outputVar = params["output_var"]?.toString()?.trim() ?: "llm_result"
        
        // 变量插值 (核心)
        val finalPrompt = context.interpolate(prompt)
        
        context.log("NodeLLMHandler: starting inference with prompt length ${finalPrompt.length}")
        
        try {
            val service = llmService
            if (service == null || !service.isReady()) {
                context.log("NodeLLMHandler: LLM service not ready, skipping")
                return node.next
            }
            // 大模型调用：在 Dispatchers.IO 下执行
            val result = withContext(Dispatchers.IO) {
                service.chat(finalPrompt)
            }
            
            // 结果保存
            context.putVariable(outputVar, result)
            context.log("NodeLLMHandler: inference completed, result saved to $outputVar")
            
        } catch (e: Exception) {
            val errorMsg = "LLM inference failed: ${e.message}"
            context.putVariable(outputVar, errorMsg)
            context.log("NodeLLMHandler: $errorMsg")
        }
        
        // 节点流转
        return node.next
    }
}
