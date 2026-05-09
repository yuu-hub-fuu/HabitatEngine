package com.ailun.habitat

import java.util.UUID

/**
 * 本地持久化的 Habitat 工作流脚本。
 */
data class HabitatWorkflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val jsonContent: String,
)
