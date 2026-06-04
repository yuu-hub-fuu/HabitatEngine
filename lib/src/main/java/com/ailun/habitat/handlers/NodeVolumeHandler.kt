package com.ailun.habitat.handlers

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_VOLUME]：控制音量及获取音量信息。
 */
class NodeVolumeHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Volume failed: 'action' parameter is empty")
            return NodeResult.failure(node.next, "Missing 'action' parameter")
        }

        val streamType = streamTypeFromParam(node.params?.get("stream")?.toString()?.trim()?.lowercase())

        val audioManager = context.appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return NodeResult.failure(node.next, "AudioManager not available")

        val outVars = mutableMapOf<String, Any?>()

        try {
            when (action) {
                "set" -> handleSet(node, audioManager, streamType, outVars)
                "get" -> handleGet(audioManager, streamType, outVars)
                "up" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    Log.i(TAG, "Volume adjusted up")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    Log.i(TAG, "Volume adjusted down")
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, 0)
                    Log.i(TAG, "Volume muted")
                }
                "unmute" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, 0)
                    Log.i(TAG, "Volume unmuted")
                }
                else -> return NodeResult.failure(node.next, "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Volume error for action '$action': ${e.message}", e)
            return NodeResult.failure(node.next, "Volume error: ${e.message}", outVars)
        }

        return NodeResult.success(node.next, outVars)
    }

    private fun handleSet(node: WorkflowNode, audioManager: AudioManager, streamType: Int, out: MutableMap<String, Any?>) {
        val valueParam = node.params?.get("value")
        val percentage = when (valueParam) {
            is Number -> valueParam.toInt()
            else -> valueParam?.toString()?.toIntOrNull()
        } ?: run { Log.e(TAG, "Volume set: missing 'value'"); return }
        val clamped = percentage.coerceIn(0, 100)
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val targetVolume = (clamped / 100.0f * maxVolume).toInt()
        audioManager.setStreamVolume(streamType, targetVolume, 0)
        out["volume_current"] = targetVolume
        out["volume_max"] = maxVolume
        Log.i(TAG, "Volume set to $clamped% ($targetVolume/$maxVolume) on stream $streamType")
    }

    private fun handleGet(audioManager: AudioManager, streamType: Int, out: MutableMap<String, Any?>) {
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        out["volume_current"] = current
        out["volume_max"] = max
        Log.i(TAG, "Volume get: current=$current, max=$max on stream $streamType")
    }

    private fun streamTypeFromParam(param: String?): Int = when (param) {
        "ring" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "system" -> AudioManager.STREAM_SYSTEM
        "call", "voice_call" -> AudioManager.STREAM_VOICE_CALL
        "dtmf" -> AudioManager.STREAM_DTMF
        "accessibility" -> AudioManager.STREAM_ACCESSIBILITY
        else -> AudioManager.STREAM_MUSIC
    }

    companion object { private const val TAG = "HabitatVolume" }
}
