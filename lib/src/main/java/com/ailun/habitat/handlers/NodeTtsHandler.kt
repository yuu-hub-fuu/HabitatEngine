package com.ailun.habitat.handlers

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * [ACTION_TEXT_TO_SPEECH]：将文本转换为语音播放。
 */
class NodeTtsHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val rawText = params["text"]?.toString()?.trim()
            ?: return NodeResult.failure(node.next, "Missing 'text' parameter",
                mapOf("tts_success" to false))

        val text = context.interpolate(rawText)
        if (text.isEmpty()) {
            return NodeResult.failure(node.next, "Text is empty after interpolation",
                mapOf("tts_success" to false))
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
                        if (!isCompleted) { isCompleted = true; continuation.resume(false) }
                        return@TextToSpeech
                    }
                    try {
                        val locale = when (languageCode) {
                            "zh" -> Locale.CHINESE; "en" -> Locale.ENGLISH
                            "ja" -> Locale.JAPANESE; "ko" -> Locale.KOREAN
                            "fr" -> Locale.FRENCH; "de" -> Locale.GERMAN
                            "es" -> Locale("es"); "pt" -> Locale("pt")
                            "ru" -> Locale("ru"); "it" -> Locale.ITALIAN
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
                            override fun onStart(uttId: String?) { Log.d(TAG, "TTS started: $uttId") }
                            override fun onDone(uttId: String?) {
                                if (!isCompleted) { isCompleted = true; continuation.resume(true) }
                            }
                            @Suppress("DEPRECATION")
                            override fun onError(uttId: String?) {
                                if (!isCompleted) { isCompleted = true; continuation.resume(false) }
                            }
                            override fun onError(uttId: String?, errorCode: Int) {
                                if (!isCompleted) { isCompleted = true; continuation.resume(false) }
                            }
                            override fun onStop(uttId: String?, interrupted: Boolean) {
                                if (!isCompleted) { isCompleted = true; continuation.resume(!interrupted) }
                            }
                        })

                        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                        if (speakResult != TextToSpeech.SUCCESS) {
                            Log.e(TAG, "TTS speak() failed: $speakResult")
                            if (!isCompleted) { isCompleted = true; continuation.resume(false) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "TTS setup error: ${e.message}", e)
                        if (!isCompleted) { isCompleted = true; continuation.resume(false) }
                    }
                }

                continuation.invokeOnCancellation {
                    try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
                }
            }

            Log.i(TAG, "TTS result: $result")
            return if (result) {
                NodeResult.success(node.next, mapOf("tts_success" to true))
            } else {
                NodeResult.failure(node.next, "TTS playback failed",
                    mapOf("tts_success" to false))
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed: ${e.message}", e)
            return NodeResult.failure(node.next, "TTS error: ${e.message}",
                mapOf("tts_success" to false, "tts_error" to (e.message ?: "Unknown")))
        } finally {
            try { tts?.shutdown() } catch (_: Exception) {}
        }
    }

    companion object { private const val TAG = "HabitatTTS" }
}
