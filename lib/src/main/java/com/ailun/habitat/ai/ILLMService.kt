package com.ailun.habitat.ai

/**
 * 端侧 LLM 服务接口
 */
interface ILLMService {
    /**
     * 加载模型
     * @param modelPath 模型文件绝对路径
     */
    suspend fun loadModel(modelPath: String): Result<Unit>

    /**
     * 发送 Prompt 并获取回答
     */
    suspend fun chat(prompt: String): String

    /**
     * 获取当前加载的模型路径
     */
    fun getModelPath(): String?

    /**
     * 检查模型是否已就绪
     */
    fun isReady(): Boolean
}
