package com.ailun.habitat.skill

object SkillRegistry {
    private val skills = mutableMapOf<String, SkillDefinition>()

    fun register(skill: SkillDefinition) { skills[skill.id] = skill }
    fun unregister(skillId: String) { skills.remove(skillId) }
    fun get(skillId: String): SkillDefinition? = skills[skillId]
    fun findForApp(packageName: String): List<SkillDefinition> =
        skills.values.filter { it.applicableApp == packageName }
    fun getAll(): List<SkillDefinition> = skills.values.toList()
    fun clear() { skills.clear() }

    fun seedBuiltInSkills() {
        // Open WiFi settings
        register(SkillDefinition(
            id = "open_wifi_settings",
            name = "打开WiFi设置",
            description = "打开系统WiFi设置页面",
            applicableApp = "com.android.settings",
            entryPageDescription = "WiFi settings page",
            requiredScreenFeatures = listOf("WiFi", "WLAN"),
            subGraph = createSimpleGraph("launch_wifi", "ACTION_LAUNCH_APP", mapOf(
                "package_name" to "com.android.settings",
                "activity" to ".Settings\$WifiSettingsActivity",
            )),
            successCriteria = com.ailun.habitat.success.SuccessCriteria(listOf(
                com.ailun.habitat.success.SuccessCondition(
                    com.ailun.habitat.success.SuccessConditionType.SCREEN_CONTAINS_TEXT,
                    "WiFi", "WiFi settings page is open"
                ),
            )),
            requiredCapabilities = listOf("APP_LAUNCH"),
        ))

        // Read verification code
        register(SkillDefinition(
            id = "read_verification_code",
            name = "读取验证码",
            description = "从短信中读取最新的验证码",
            entryPageDescription = "Any page",
            subGraph = createSimpleGraph("read_code", "ACTION_READ_SMS", mapOf(
                "filter_by" to "latest", "extract_code" to true,
            )),
            successCriteria = com.ailun.habitat.success.SuccessCriteria(listOf(
                com.ailun.habitat.success.SuccessCondition(
                    com.ailun.habitat.success.SuccessConditionType.VARIABLE_SATISFIES,
                    "sms_found == true", "SMS found"
                ),
            )),
            requiredCapabilities = listOf("SMS_READ"),
        ))

        // Take screenshot
        register(SkillDefinition(
            id = "take_screenshot",
            name = "截屏",
            description = "截取当前屏幕并保存",
            subGraph = createSimpleGraph("screenshot", "ACTION_SCREENSHOT", mapOf(
                "format" to "base64", "quality" to 80,
            )),
            successCriteria = com.ailun.habitat.success.SuccessCriteria(listOf(
                com.ailun.habitat.success.SuccessCondition(
                    com.ailun.habitat.success.SuccessConditionType.VARIABLE_SATISFIES,
                    "screenshot_success == true", "Screenshot captured"
                ),
            )),
            requiredCapabilities = listOf("SCREEN_SCREENSHOT"),
        ))

        // Toggle WiFi
        register(SkillDefinition(
            id = "toggle_wifi",
            name = "切换WiFi",
            description = "打开/关闭WiFi",
            subGraph = createSimpleGraph("wifi", "ACTION_WIFI", mapOf("action" to "toggle")),
            requiredCapabilities = listOf("WIFI_CONTROL"),
        ))

        // Toggle Bluetooth
        register(SkillDefinition(
            id = "toggle_bluetooth",
            name = "切换蓝牙",
            description = "打开/关闭蓝牙",
            subGraph = createSimpleGraph("bt", "ACTION_BLUETOOTH", mapOf("action" to "toggle")),
            requiredCapabilities = listOf("BLUETOOTH_CONTROL"),
        ))
    }

    private fun createSimpleGraph(
        nodeId: String, type: String, params: Map<String, Any>,
    ): com.ailun.habitat.WorkflowGraph {
        val node = com.ailun.habitat.WorkflowNode().apply {
            id = nodeId; this.type = type; this.params = params
        }
        return com.ailun.habitat.WorkflowGraph().apply {
            startNodeId = nodeId
            nodes = mapOf(nodeId to node)
        }
    }
}
