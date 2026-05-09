package com.ailun.habitat.api

import android.content.Context

/**
 * 触发器事件源接口。每种触发器类型实现此接口，负责监听系统事件并回调。
 *
 * 生命周期：start() → [多次 onEvent] → stop()
 */
interface ITriggerSource {
    /** 触发器类型标识，对应 [com.ailun.habitat.TriggerConfig.type]。 */
    val type: String

    /** 启动事件监听。 */
    fun start(context: Context, onEvent: (TriggerEvent) -> Unit)

    /** 停止事件监听并释放资源。 */
    fun stop(context: Context)
}

/**
 * 触发器产生的原始事件，由 [ITriggerSource] 回调给 [com.ailun.habitat.app.TriggerManager]。
 */
data class TriggerEvent(
    val type: String,
    val params: Map<String, Any> = emptyMap(),
) {
    val packageName: String?
        get() = params["package"]?.toString()

    val smsBody: String?
        get() = params["body"]?.toString()

    val smsSender: String?
        get() = params["sender"]?.toString()

    val clipboardText: String?
        get() = params["text"]?.toString()

    val keyCode: Int?
        get() = (params["keycode"] as? Number)?.toInt()

    val keyAction: Int?
        get() = (params["action"] as? Number)?.toInt()
}
