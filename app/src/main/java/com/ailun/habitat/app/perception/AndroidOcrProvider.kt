package com.ailun.habitat.app.perception

import android.content.Context
import android.graphics.BitmapFactory
import com.ailun.habitat.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * OCR provider placeholder.
 *
 * Full implementation would use ML Kit Text Recognition v2:
 *   implementation("com.google.mlkit:text-recognition:16.0.0")
 *
 * For now, provides a stub that returns empty results.
 * The PerceptionEngine degrades gracefully to a11y-only mode when OCR is unavailable.
 *
 * To enable ML Kit OCR:
 * 1. Add the ML Kit dependency to app/build.gradle.kts
 * 2. Initialize TextRecognizer in onReady()
 * 3. Call recognizer.process(InputImage.fromBitmap(...)) in recognize()
 */
class AndroidOcrProvider(
    private val context: Context,
) : IOcrProvider {

    private var initialized = false

    override fun isReady(): Boolean = initialized

    override suspend fun recognize(screenshot: ScreenshotData): OcrResult {
        return withContext(Dispatchers.IO) {
            if (!initialized) {
                return@withContext OcrResult(emptyList(), "")
            }

            try {
                val bitmap = BitmapFactory.decodeByteArray(
                    screenshot.compressedBytes, 0, screenshot.compressedBytes.size
                )
                // TODO: ML Kit Text Recognition
                // val image = InputImage.fromBitmap(bitmap, 0)
                // val recognizedText = recognizer.process(image).await()
                // val blocks = recognizedText.textBlocks.map { ... }
                bitmap?.recycle()
                OcrResult(emptyList(), "")
            } catch (e: Exception) {
                OcrResult(emptyList(), "")
            }
        }
    }
}
