package com.ailun.habitat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.CompletableDeferred

/**
 * 无障碍手势派发工具，使用标准 Android [AccessibilityService] API。
 */
object HabitatAccessibility {

    suspend fun dispatchTap(service: AccessibilityService, x: Int, y: Int): Boolean {
        if (x < 0 || y < 0) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100L))
            .build()
        return dispatchGestureAwait(service, gesture)
    }

    suspend fun dispatchSwipe(
        service: AccessibilityService,
        x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long,
    ): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L)))
            .build()
        return dispatchGestureAwait(service, gesture)
    }

    private suspend fun dispatchGestureAwait(
        service: AccessibilityService,
        gesture: GestureDescription,
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) { deferred.complete(true) }
            override fun onCancelled(gesture: GestureDescription?) { deferred.complete(false) }
        }, null)
        return deferred.await()
    }
}
