package com.ailun.habitat.handlers

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * [ACTION_TEXT_TO_SPEECH]：将文本转换为语音播放。
 *
 * params：
 * - `text`（必填）：要朗读的文本
 * - `language`（可选）："zh" / "en" / "ja" 等，默认使用设备默认语言
 * - `pitch`（可选）：音调，1.0 为正常，默认 1.0
 * - `speed`（可选）：语速，1.0 为正常，默认 1.0
 */
class NodeTtsHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return node.nextResult()

        val rawText = params["text"]?.toString()?.trim() ?: run {
            Log.w(TAG, "No text specified")
            context.variables["tts_success"] = false
            return node.nextResult()
        }

        val text = context.interpolate(rawText)
        if (text.isEmpty()) {
            Log.w(TAG, "Text is empty after interpolation")
            context.variables["tts_success"] = false
            return node.nextResult()
        }

        val languageCode = params["language"]?.toString()?.trim()?.lowercase()
        val pitch = (params["pitch"] as? Number)?.toFloat() ?: 1.0f
        val speed = (params["speed"] as? Number)?.toFloat() ?: 1.0f

        var tts: TextToSpeech? = null

        try {
            val result = suspendCancellableCoroutine { continuation ->
                val utteranceId = UUID.randomUUID().toString()
                var isCompleted = false

                tts = TextToSpeech(context.appContext) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        Log.e(TAG, "TTS initialization failed with status: $status")
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resume(false)
                        }
                        return@TextToSpeech
                    }

                    try {
                        // Set language based on param
                        val locale = when (languageCode) {
                            "zh" -> Locale.CHINESE
                            "en" -> Locale.ENGLISH
                            "ja" -> Locale.JAPANESE
                            "ko" -> Locale.KOREAN
                            "fr" -> Locale.FRENCH
                            "de" -> Locale.GERMAN
                            "es" -> Locale("es")
                            "pt" -> Locale("pt")
                            "ru" -> Locale("ru")
                            "it" -> Locale.ITALIAN
                            else -> Locale.getDefault()
                        }

                        val langResult = tts?.setLanguage(locale)
                        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                            langResult == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            Log.w(TAG, "Language $languageCode not supported, using default")
                            tts?.setLanguage(Locale.getDefault())
                        }

                        tts?.setPitch(pitch)
                        tts?.setSpeechRate(speed)

                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(uttId: String?) {
                                Log.d(TAG, "TTS started: $uttId")
                            }

                            override fun onDone(uttId: String?) {
                                Log.d(TAG, "TTS completed: $uttId")
                                if (!isCompleted) {
                                    isCompleted = true
                                    continuation.resume(true)
                                }
                            }

                            @Suppress("DEPRECATION")
                            override fun onError(uttId: String?) {
                                Log.e(TAG, "TTS error: $uttId")
                                if (!isCompleted) {
                                    isCompleted = true
                                    continuation.resume(false)
                                }
                            }

                            override fun onError(uttId: String?, errorCode: Int) {
                                Log.e(TAG, "TTS error: $uttId, code=$errorCode")
                                if (!isCompleted) {
                                    isCompleted = true
                                    continuation.resume(false)
                                }
                            }

                            override fun onStop(uttId: String?, interrupted: Boolean) {
                                Log.d(TAG, "TTS stopped: $uttId, interrupted=$interrupted")
                                if (!isCompleted) {
                                    isCompleted = true
                                    continuation.resume(!interrupted)
                                }
                            }
                        })

                        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                        if (speakResult != TextToSpeech.SUCCESS) {
                            Log.e(TAG, "TTS speak() failed with result: $speakResult")
                            if (!isCompleted) {
                                isCompleted = true
                                continuation.resume(false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "TTS error during setup: ${e.message}", e)
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resume(false)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    try {
                        tts.stop()
                        tts.shutdown()
                    } catch (_: Exception) {
                        // ignore
                    }
                }
            }

            context.variables["tts_success"] = result
            Log.i(TAG, "TTS result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed: ${e.message}", e)
            context.variables["tts_success"] = false
            context.variables["tts_error"] = e.message ?: "Unknown error"
        } finally {
            try {
                tts?.shutdown()
            } catch (_: Exception) {
                // ignore
            }
        }

        return node.nextResult()
    }

    companion object {
        private const val TAG = "HabitatTTS"
    }
}
