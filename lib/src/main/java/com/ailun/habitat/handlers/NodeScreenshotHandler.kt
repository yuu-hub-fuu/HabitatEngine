package com.ailun.habitat.handlers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IShellExecutor
import java.io.ByteArrayOutputStream
import java.io.File

class NodeScreenshotHandler(
    private val shellExecutor: IShellExecutor? = null
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.success(node.next)

        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null }
        val format = params["format"]?.toString()?.trim()?.lowercase() ?: "base64"
        val quality = (params["quality"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 80

        // Use a world-writable path so the shell process (uid=2000) can write to it.
        // App private dirs like cacheDir are NOT writable by shell.
        val fileName = "habitat_screenshot_${System.currentTimeMillis()}.png"
        val extDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        if (!extDir.exists()) extDir.mkdirs()
        val outFile = File(extDir, fileName)
        context.log("Screenshot: output=$outFile")

        try {
            if (shellExecutor != null) {
                val cmd = "screencap -p ${outFile.absolutePath}"
                context.log("Screenshot: exec '$cmd'")
                val result = shellExecutor.exec(cmd, asRoot = false)
                context.log("Screenshot: shell result='${result.take(200)}' (len=${result.length})")

                if (result.contains("Error", ignoreCase = true) ||
                    result.contains("denied", ignoreCase = true) ||
                    result.contains("not found", ignoreCase = true)
                ) {
                    Log.e(TAG, "Screenshot shell error: $result")
                    context.variables["screenshot_success"] = false
                    context.variables["screenshot_error"] = "Shell: $result"
                    return NodeResult.success(node.next)
                }
            } else {
                context.log("Screenshot: no shell executor, trying Runtime.exec")
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("screencap", "-p", outFile.absolutePath))
                    process.waitFor()
                    val err = process.errorStream.bufferedReader().readText()
                    if (err.isNotBlank()) {
                        context.log("Screenshot: stderr='${err.take(200)}'")
                    }
                } catch (e: Exception) {
                    context.log("Screenshot: Runtime.exec failed: ${e.message}")
                    context.variables["screenshot_success"] = false
                    context.variables["screenshot_error"] = "exec: ${e.message}"
                    return NodeResult.success(node.next)
                }
            }

            // Small delay for file system sync
            kotlinx.coroutines.delay(200)

            val exists = outFile.exists()
            val size = if (exists) outFile.length() else -1L
            context.log("Screenshot: file exists=$exists, size=$size")

            if (!exists || size == 0L) {
                Log.e(TAG, "Screenshot: no output file at $outFile")
                context.variables["screenshot_success"] = false
                context.variables["screenshot_error"] = "文件未生成 ($outFile)"
                return NodeResult.success(node.next)
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outFile.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            context.log("Screenshot: decoded ${width}x${height}")

            context.variables["screenshot_width"] = width
            context.variables["screenshot_height"] = height

            val result: String = when (format) {
                "file" -> outFile.absolutePath
                else -> {
                    val bitmap = BitmapFactory.decodeFile(outFile.absolutePath)
                    if (bitmap != null) {
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                        val bytes = baos.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        bitmap.recycle()
                        baos.close()
                        base64
                    } else {
                        val bytes = outFile.readBytes()
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }
            }

            context.variables[outputVar ?: "screenshot_base64"] = result
            context.variables["screenshot_success"] = true
            context.log("Screenshot: success, ${width}x${height}, format=$format")
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed: ${e.message}", e)
            context.variables["screenshot_success"] = false
            context.variables["screenshot_error"] = e.message ?: "Unknown"
            context.log("Screenshot: exception ${e.message}")
        }

        return NodeResult.success(node.next)
    }

    companion object {
        private const val TAG = "HabitatScreenshot"
    }
}
