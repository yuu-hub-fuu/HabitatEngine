package com.ailun.habitat.app.triggers

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ailun.habitat.TriggerConfig
import com.ailun.habitat.api.ITriggerSource
import com.ailun.habitat.api.TriggerEvent

/**
 * 定时触发器 — 基于 Handler 的周期性定时。
 * 对于需要持久化的定时任务，应改为 AlarmManager；当前实现适用于应用进程内的简单定时。
 */
class TimerTriggerSource : ITriggerSource {
    override val type: String = TriggerConfig.TYPE_TIMER

    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var onEvent: ((TriggerEvent) -> Unit)? = null
    private var intervalMs: Long = 60000L
    private var isRepeat: Boolean = false

    override fun start(context: Context, onEvent: (TriggerEvent) -> Unit) {
        // Timer source uses registration-time metadata; per-entry interval is
        // handled by TriggerManager re-starting from the trigger config.
        this.onEvent = onEvent
        // Use a default 5-second interval for the source loop;
        // TriggerManager checks each registered timer trigger on every tick.
        intervalMs = 5000L
        isRepeat = true

        val h = Handler(Looper.getMainLooper())
        handler = h

        val r = object : Runnable {
            override fun run() {
                onEvent(TriggerEvent(type = TriggerConfig.TYPE_TIMER))
                if (isRepeat) h.postDelayed(this, intervalMs)
            }
        }
        runnable = r
        h.post(r)
    }

    override fun stop(context: Context) {
        handler?.removeCallbacks(runnable ?: return)
        handler = null
        runnable = null
        onEvent = null
    }
}
