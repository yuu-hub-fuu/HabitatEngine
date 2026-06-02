package com.ailun.habitat.app

import android.content.Context
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Habitat 的 Shizuku 用户服务，运行在 Shizuku 进程中，拥有 Shell 级权限。
 */
class HabitatShizukuService(private val context: Context) : IHabitatShizukuService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) return "Error: Empty command"
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            when {
                exitCode == 0 -> stdout.ifEmpty { "OK" }
                stderr.isNotEmpty() -> "Error (code $exitCode): $stderr"
                else -> "Error (code $exitCode)"
            }
        } catch (e: SecurityException) {
            "Error: Permission denied - ${e.message}"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun exit() {
        try { Thread.sleep(100) } catch (_: InterruptedException) {}
        System.exit(0)
    }

    override fun asBinder(): IBinder = super.asBinder()
}
