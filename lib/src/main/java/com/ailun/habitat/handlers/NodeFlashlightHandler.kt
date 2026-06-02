package com.ailun.habitat.handlers

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor

/**
 * [ACTION_FLASHLIGHT]：控制手电筒开关。
 *
 * params：`action`（必填："on"/"off"/"toggle"）
 */
class NodeFlashlightHandler(
    private val provider: IAccessibilityProvider? = null,
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val action = node.params?.get("action")?.toString()?.trim()?.lowercase().orEmpty()
        if (action.isEmpty()) {
            Log.e(TAG, "Flashlight failed: 'action' parameter is empty")
            return node.next
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.e(TAG, "Flashlight failed: requires API 23+ (camera2), current is ${Build.VERSION.SDK_INT}")
            return node.next
        }

        val cameraManager = context.appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            Log.e(TAG, "Flashlight failed: unable to get CameraManager service")
            return node.next
        }

        try {
            val cameraId = findFlashCameraId(cameraManager)
            if (cameraId == null) {
                Log.e(TAG, "Flashlight failed: no camera with flash unit found")
                return node.next
            }

            var targetOn: Boolean

            when (action) {
                "on" -> {
                    targetOn = true
                }
                "off" -> {
                    targetOn = false
                }
                "toggle" -> {
                    // Check current torch state by checking the first available flash camera
                    targetOn = try {
                        val currentFlashMode = cameraManager.getCameraCharacteristics(cameraId)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                        // We can't easily query current torch state on all devices;
                        // use a tracked variable instead
                        val isOn = context.variables["flashlight_on"] as? Boolean == true
                        !isOn
                    } catch (e: Exception) {
                        // Default to turning on if we can't determine state
                        true
                    }
                }
                else -> {
                    Log.e(TAG, "Flashlight failed: unknown action '$action'")
                    return node.next
                }
            }

            cameraManager.setTorchMode(cameraId, targetOn)
            context.variables["flashlight_on"] = targetOn
            Log.i(TAG, "Flashlight turned ${if (targetOn) "on" else "off"}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Flashlight error: missing CAMERA permission - ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight error for action '$action': ${e.message}", e)
        }

        return node.next
    }

    /**
     * Find the first rear-facing camera that has a flash unit.
     */
    private fun findFlashCameraId(cameraManager: CameraManager): String? {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (hasFlash) {
                    // Prefer rear-facing camera, but accept any with flash
                    if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        Log.d(TAG, "Found rear-facing camera with flash: $id")
                        return id
                    }
                }
            }

            // Fallback: any camera with flash
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (hasFlash) {
                    Log.d(TAG, "Found camera with flash (non-rear): $id")
                    return id
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating cameras: ${e.message}", e)
        }
        return null
    }

    companion object {
        private const val TAG = "HabitatFlashlight"
    }
}
