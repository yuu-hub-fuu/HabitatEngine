package com.ailun.habitat.app

import android.content.Context
import com.ailun.habitat.HabitatExecutionService
import com.ailun.habitat.NodeHandlerFactory
import com.ailun.habitat.TriggerConfig
import com.ailun.habitat.api.ITriggerSource
import com.ailun.habitat.api.TriggerEvent
import com.ailun.habitat.app.bridge.AppAccessibilityProvider
import com.ailun.habitat.app.bridge.ShizukuShellExecutor
import com.ailun.habitat.app.bridge.applyAppHandlers
import com.ailun.habitat.app.triggers.*

/**
 * 触发器调度中心。维护已注册工作流列表，事件匹配 → 调用执行服务。
 *
 * 同一个 start() 路径同时服务手动和自动触发。
 */
object TriggerManager {
    private const val TAG = "TriggerManager"

    private data class TriggerEntry(
        val workflowId: String,
        val config: TriggerConfig,
        val jsonContent: String,
    )

    private val entries = mutableMapOf<String, TriggerEntry>()
    private val sourceInstances = mutableMapOf<String, ITriggerSource>()
    private var factory: NodeHandlerFactory? = null

    /** 注册工作流触发器，有 trigger 字段的 JSON 自动注册。 */
    fun register(
        workflowId: String,
        config: TriggerConfig,
        jsonContent: String,
        context: Context,
    ) {
        entries[workflowId] = TriggerEntry(workflowId, config, jsonContent)

        // Lazy-create and start the trigger source for this type
        val source = sourceInstances.getOrPut(config.type) {
            createSource(config.type)
        }
        if (entries.values.count { it.config.type == config.type } <= 1) {
            source.start(context) { event -> onTriggerEvent(event, context) }
        }

        HabitatLogger.habitat("Trigger registered: '$workflowId' type=${config.type}")
    }

    /** 取消工作流触发器注册。 */
    fun unregister(workflowId: String, context: Context) {
        val entry = entries.remove(workflowId) ?: return
        val type = entry.config.type

        // If no more workflows use this trigger type, stop the source
        if (entries.values.none { it.config.type == type }) {
            sourceInstances[type]?.stop(context)
            sourceInstances.remove(type)
        }

        HabitatLogger.habitat("Trigger unregistered: '$workflowId' type=$type")
    }

    /** 清除所有注册。 */
    fun unregisterAll(context: Context) {
        sourceInstances.values.forEach { it.stop(context) }
        sourceInstances.clear()
        entries.clear()
        HabitatLogger.habitat("All triggers unregistered")
    }

    /** 检查工作流是否有活跃触发器。 */
    fun isRegistered(workflowId: String): Boolean = entries.containsKey(workflowId)

    /** 获取已注册触发器的工作流 ID 列表。 */
    fun registeredWorkflowIds(): Set<String> = entries.keys.toSet()

    /** 获取工作流的触发器配置，未注册则返回 null。 */
    fun triggerConfigFor(workflowId: String): TriggerConfig? = entries[workflowId]?.config

    private fun createSource(type: String): ITriggerSource = when (type) {
        TriggerConfig.TYPE_NOTIFICATION -> NotificationTriggerSource()
        TriggerConfig.TYPE_SMS -> SmsTriggerSource()
        TriggerConfig.TYPE_TIMER -> TimerTriggerSource()
        TriggerConfig.TYPE_CLIPBOARD -> ClipboardTriggerSource()
        TriggerConfig.TYPE_KEY -> KeyEventTriggerSource()
        else -> throw IllegalArgumentException("Unknown trigger type: $type")
    }

    private fun onTriggerEvent(event: TriggerEvent, context: Context) {
        val matched = entries.values.filter { entry ->
            matchEvent(entry.config, event)
        }

        for (entry in matched) {
            HabitatLogger.habitat("Trigger fired: '${entry.workflowId}' type=${entry.config.type}")
            val f = factory ?: buildFactory(context).also { factory = it }

            // Inject trigger event data as context variables
            val vars = mutableMapOf<String, Any>(
                "trigger_type" to event.type,
            )
            event.params.forEach { (k, v) -> vars["trigger_$k"] = v }

            if (HabitatExecutionService.isRunning(entry.workflowId) && entry.config.repeat) {
                HabitatExecutionService.start(
                    workflowId = entry.workflowId + "_" + System.currentTimeMillis(),
                    jsonContent = entry.jsonContent,
                    androidContext = context,
                    factory = f,
                    initialVars = vars,
                )
            } else {
                HabitatExecutionService.start(
                    workflowId = entry.workflowId,
                    jsonContent = entry.jsonContent,
                    androidContext = context,
                    factory = f,
                    initialVars = vars,
                )
            }
        }
    }

    private fun matchEvent(config: TriggerConfig, event: TriggerEvent): Boolean {
        if (config.type != event.type) return false
        return when (config.type) {
            TriggerConfig.TYPE_NOTIFICATION -> {
                val filterPkg = config.packageFilter
                filterPkg == null || filterPkg == event.packageName
            }
            TriggerConfig.TYPE_SMS -> {
                val filterPkg = config.packageFilter
                filterPkg == null || filterPkg == event.packageName
            }
            TriggerConfig.TYPE_KEY -> {
                val keycode = config.keycode
                keycode == null || keycode == event.keyCode
            }
            TriggerConfig.TYPE_TIMER,
            TriggerConfig.TYPE_CLIPBOARD -> true
            else -> false
        }
    }

    private fun buildFactory(context: Context): NodeHandlerFactory {
        return NodeHandlerFactory(
            AppAccessibilityProvider,
            ShizukuShellExecutor(context.applicationContext),
        ).apply { applyAppHandlers(context.applicationContext) }
    }
}
