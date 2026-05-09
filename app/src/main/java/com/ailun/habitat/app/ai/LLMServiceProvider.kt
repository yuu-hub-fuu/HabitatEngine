package com.ailun.habitat.app.ai

import android.content.Context
import com.ailun.habitat.ai.ILLMService

object LLMServiceProvider {
    @Volatile
    private var instance: ILLMService? = null

    fun get(context: Context): ILLMService =
        instance ?: synchronized(this) {
            instance ?: LiteRtLLMServiceImpl(context).also { instance = it }
        }
}
