package com.ailun.habitat.app.ai

import android.content.Context
import android.util.Log
import com.ailun.habitat.ai.ILLMService
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtLLMServiceImpl(private val context: Context) : ILLMService {

    private var engine: Engine? = null
    private var currentModelPath: String? = null

    override suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Model file not found: $modelPath"))
            }

            engine?.close()
            engine = null

            val config = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU()
            )
            engine = Engine(config).also { it.initialize() }
            currentModelPath = modelPath
            Log.i(TAG, "Model loaded: $modelPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        val eng = engine ?: return@withContext "Error: Model not loaded"
        try {
            val conversation = eng.createConversation()
            val response = conversation.sendMessage(Message.user(prompt))
            response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    override fun getModelPath(): String? = currentModelPath

    override fun isReady(): Boolean = engine != null

    companion object {
        private const val TAG = "LiteRtLLM"
    }
}
