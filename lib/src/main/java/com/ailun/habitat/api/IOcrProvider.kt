package com.ailun.habitat.api

data class OcrResult(
    val textBlocks: List<TextBlock>,
    val fullText: String,
)

data class TextBlock(
    val text: String,
    val boundingBox: ContentRect,
    val confidence: Float,
)

data class ContentRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    fun contains(x: Int, y: Int) = x in left..right && y in top..bottom
}

/**
 * Platform-agnostic OCR interface.
 * App module provides ML Kit or Tesseract implementation.
 */
interface IOcrProvider {
    suspend fun recognize(screenshot: ScreenshotData): OcrResult
    fun isReady(): Boolean
}
