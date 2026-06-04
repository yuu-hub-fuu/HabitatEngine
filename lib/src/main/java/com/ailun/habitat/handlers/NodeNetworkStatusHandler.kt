package com.ailun.habitat.handlers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * [ACTION_NETWORK_STATUS]：查询网络状态和设备 IP。
 */
class NodeNetworkStatusHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val action = node.params?.get("action")?.toString()?.lowercase() ?: "status"
        val cm = context.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        try {
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            val connected = caps != null
            val type = when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }

            val outVars = mutableMapOf<String, Any?>(
                "network_connected" to connected,
                "network_type" to type,
                "network_success" to true,
            )
            if (action == "ip" || action == "all") {
                outVars["wifi_ip"] = getWifiIp(context)
                outVars["mobile_ip"] = getMobileIp()
            }
            context.log("NetworkStatus connected=$connected type=$type")
            return NodeResult.success(node.next, outVars)
        } catch (e: Exception) {
            context.log("NetworkStatus error: ${e.message}")
            return NodeResult.failure(node.next, "Network status error: ${e.message}",
                mapOf("network_connected" to false, "network_error" to e.message,
                    "network_success" to false))
        }
    }

    private fun getWifiIp(context: WorkflowContext): String {
        return try {
            val wm = context.appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wm?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) String.format("%d.%d.%d.%d", ip and 0xFF, (ip shr 8) and 0xFF, (ip shr 16) and 0xFF, (ip shr 24) and 0xFF) else ""
        } catch (_: Exception) { "" }
    }

    private fun getMobileIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.filter { !it.isLoopbackAddress }
                ?.map { it.hostAddress }
                ?.firstOrNull() ?: ""
        } catch (_: Exception) { "" }
    }
}
