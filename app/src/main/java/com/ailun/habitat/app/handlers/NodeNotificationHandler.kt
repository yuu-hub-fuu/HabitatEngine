package com.ailun.habitat.app.handlers

import android.app.NotificationChannel
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ailun.habitat.R

/**
 * [ACTION_SEND_NOTIFICATION]：发送系统通知。
 */
class NodeNotificationHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): String? {
        val app = context.appContext
        val title = node.params?.get("title")?.toString()
            ?: app.getString(R.string.habitat_notification_title_default)
        val message = node.params?.get("message")?.toString()
            ?: app.getString(R.string.habitat_notification_message_default)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = app.getString(R.string.habitat_notification_channel)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_workflows)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
        return node.next
    }

    companion object {
        private const val CHANNEL_ID = "habitat_notifications"
    }
}
