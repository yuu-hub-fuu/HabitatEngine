package com.ailun.habitat.app.perception

import android.graphics.Bitmap
import com.ailun.habitat.api.IScreenshotProvider
import com.ailun.habitat.api.ScreenshotData
import java.io.ByteArrayOutputStream

/**
 * Screenshot provider using Android's MediaProjection or AccessibilityService.
 *
 * Falls back to AccessibilityService.takeScreenshot() on API 34+ if available,
 * otherwise uses screencap shell command via Shizuku/root.
 */
class AndroidScreenshotProvider(
    private val shellExecutor: com.ailun.habitat.api.IShellExecutor? = null,
) : IScreenshotProvider {

    override fun isReady(): Boolean = shellExecutor != null

    override suspend fun capture(): ScreenshotData? {
        return try {
            // Strategy 1: Base64-encoded screencap via shell (cross-device)
            if (shellExecutor != null) {
                val output = shellExecutor.exec("screencap -p | base64", asRoot = false)
                if (output.isNotEmpty() && !output.contains("Error")) {
                    val bytes = android.util.Base64.decode(output.trim(), android.util.Base64.DEFAULT)
                    if (bytes.isNotEmpty()) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            val compressed = compressToJpeg(bmp, 80)
                            val data = ScreenshotData(compressed, bmp.width, bmp.height)
                            bmp.recycle()
                            return data
                        }
                    }
                }
            }

            // Strategy 2: Save to temp file and read back (fallback)
            val tmpFile = java.io.File("/data/local/tmp/habitat_screenshot.png")
            if (shellExecutor != null) {
                shellExecutor.exec("screencap -p ${tmpFile.absolutePath}", asRoot = false)
                if (tmpFile.exists() && tmpFile.length() > 0) {
                    val bytes = tmpFile.readBytes()
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val compressed = compressToJpeg(bmp, 80)
                        val data = ScreenshotData(compressed, bmp.width, bmp.height)
                        bmp.recycle()
                        tmpFile.delete()
                        return data
                    }
                }
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotProvider", "Screenshot failed: ${e.message}")
            null
        }
    }

    private fun compressToJpeg(bmp: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
