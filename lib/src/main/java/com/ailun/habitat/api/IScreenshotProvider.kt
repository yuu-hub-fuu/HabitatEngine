package com.ailun.habitat.api

/**
 * Platform-agnostic screenshot capture interface.
 */
interface IScreenshotProvider {
    suspend fun capture(): ScreenshotData?
    fun isReady(): Boolean
}

data class ScreenshotData(
    val compressedBytes: ByteArray,
    val width: Int,
    val height: Int,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean =
        other is ScreenshotData && compressedBytes.contentEquals(other.compressedBytes) &&
        width == other.width && height == other.height

    override fun hashCode(): Int =
        compressedBytes.contentHashCode() * 31 + width * 31 + height
}
