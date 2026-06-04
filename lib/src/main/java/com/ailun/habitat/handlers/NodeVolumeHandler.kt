package com.ailun.habitat.handlers

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_VOLUME]：控制音量及获取音量信息。
 *
 * params：
 *   - `action`（必填："set"/"get"/"up"/"down"/"mute"/"unmute"）
 *   - `stream`（可选："music"/"ring"/"alarm"/"notification"/"system"/"call"，默认 "music"）
 *   - `value`（用于 set：0-100 百分比）
 */
class NodeVolumeHandler(
    private val provider: IAccessibilityProvider? = null,
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Volume failed: 'action' parameter is empty")
            return NodeResult.success(node.next)
        }

        val streamType = streamTypeFromParam(
            node.params?.get("stream")?.toString()?.trim()?.lowercase()
        )

        val audioManager = context.appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            Log.e(TAG, "Volume failed: unable to get AudioManager service")
            return NodeResult.success(node.next)
        }

        try {
            when (action) {
                "set" -> handleSet(node, context, audioManager, streamType)
                "get" -> handleGet(context, audioManager, streamType)
                "up" -> handleAdjust(audioManager, streamType, AudioManager.ADJUST_RAISE)
                "down" -> handleAdjust(audioManager, streamType, AudioManager.ADJUST_LOWER)
                "mute" -> handleMute(audioManager, streamType, true)
                "unmute" -> handleMute(audioManager, streamType, false)
                else -> {
                    Log.e(TAG, "Volume failed: unknown action '$action'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Volume error for action '$action': ${e.message}", e)
        }

        return NodeResult.success(node.next)
    }

    private fun handleSet(
        node: WorkflowNode,
        context: WorkflowContext,
        audioManager: AudioManager,
        streamType: Int,
    ) {
        val valueParam = node.params?.get("value")
        val percentage = when (valueParam) {
            is Number -> valueParam.toInt()
            else -> valueParam?.toString()?.toIntOrNull()
        } ?: run {
            Log.e(TAG, "Volume set failed: 'value' parameter is missing or invalid")
            return
        }

        val clamped = percentage.coerceIn(0, 100)
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val targetVolume = (clamped / 100.0f * maxVolume).toInt()

        audioManager.setStreamVolume(streamType, targetVolume, 0)
        context.variables["volume_current"] = targetVolume
        context.variables["volume_max"] = maxVolume
        Log.i(TAG, "Volume set to $clamped% ($targetVolume/$maxVolume) on stream $streamType")
    }

    private fun handleGet(
        context: WorkflowContext,
        audioManager: AudioManager,
        streamType: Int,
    ) {
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        context.variables["volume_current"] = current
        context.variables["volume_max"] = max
        Log.i(TAG, "Volume get: current=$current, max=$max on stream $streamType")
    }

    private fun handleAdjust(
        audioManager: AudioManager,
        streamType: Int,
        direction: Int,
    ) {
        audioManager.adjustStreamVolume(streamType, direction, AudioManager.FLAG_SHOW_UI)
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        val dirName = if (direction == AudioManager.ADJUST_RAISE) "up" else "down"
        Log.i(TAG, "Volume adjusted $dirName: current=$current, max=$max on stream $streamType")
    }

    private fun handleMute(
        audioManager: AudioManager,
        streamType: Int,
        mute: Boolean,
    ) {
        if (mute) {
            audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, 0)
        } else {
            audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, 0)
        }
        val label = if (mute) "muted" else "unmuted"
        Log.i(TAG, "Volume $label on stream $streamType")
    }

    private fun streamTypeFromParam(param: String?): Int {
        return when (param) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "system" -> AudioManager.STREAM_SYSTEM
            "call", "voice_call" -> AudioManager.STREAM_VOICE_CALL
            "dtmf" -> AudioManager.STREAM_DTMF
            "accessibility" -> AudioManager.STREAM_ACCESSIBILITY
            else -> AudioManager.STREAM_MUSIC
        }
    }

    companion object {
        private const val TAG = "HabitatVolume"
    }
}
