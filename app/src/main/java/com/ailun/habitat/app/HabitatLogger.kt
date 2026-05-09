package com.ailun.habitat.app

import android.util.Log

/**
 * Habitat 轻量日志工具。
 * 所有日志同时输出到 logcat 和内存缓冲区（供应用内日志查看器使用）。
 */
object HabitatLogger {
    private val buffer = mutableListOf<String>()
    private const val MAX_BUFFER = 2000

    @Volatile var enabled = true

    fun d(tag: String, message: String) { log("D", tag, message) }
    fun i(tag: String, message: String) { log("I", tag, message) }
    fun w(tag: String, message: String) { log("W", tag, message) }
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message: ${throwable.message}" else message
        log("E", tag, msg)
    }

    fun habitat(message: String) = i("Habitat", message)

    fun getRecentLogs(count: Int = 100): List<String> = synchronized(buffer) {
        buffer.takeLast(count).toList()
    }

    private fun log(level: String, tag: String, message: String) {
        if (!enabled) return
        val entry = "[$level] $tag: $message"
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message)
            "E" -> Log.e(tag, message)
        }
        synchronized(buffer) {
            buffer.add(entry)
            if (buffer.size > MAX_BUFFER) buffer.removeAt(0)
        }
    }
}
