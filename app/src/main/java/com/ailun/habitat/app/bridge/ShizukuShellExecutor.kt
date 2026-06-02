package com.ailun.habitat.app.bridge

import android.content.Context
import com.ailun.habitat.api.IShellExecutor
import com.ailun.habitat.app.HabitatShellManager

/** Habitat 自有 Shell 执行器，基于 [HabitatShellManager] 的 Shizuku/Root 双通道。 */
class ShizukuShellExecutor(private val context: Context) : IShellExecutor {
    override suspend fun exec(command: String, asRoot: Boolean): String {
        val mode = if (asRoot) HabitatShellManager.ShellMode.ROOT else HabitatShellManager.ShellMode.AUTO
        return HabitatShellManager.execShellCommand(context, command, mode)
    }
}
