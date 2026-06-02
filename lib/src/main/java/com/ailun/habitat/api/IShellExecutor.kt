package com.ailun.habitat.api

/**
 * Shell 命令执行器接口，解耦引擎与宿主平台的 Shell 实现。
 */
interface IShellExecutor {
    /** 执行 shell 命令并返回输出字符串。 */
    suspend fun exec(command: String, asRoot: Boolean = false): String
}
