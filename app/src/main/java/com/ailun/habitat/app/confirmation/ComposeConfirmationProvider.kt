package com.ailun.habitat.app.confirmation

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.ailun.habitat.api.ConfirmationRequest
import com.ailun.habitat.api.ConfirmationResponse
import com.ailun.habitat.api.IConfirmationProvider
import com.ailun.habitat.capability.RiskLevel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android Compose/AlertDialog-based confirmation provider.
 *
 * Shows a system alert dialog (so it works from any context, including
 * background services and overlay windows) with action details and
 * Approve/Deny buttons. The risk level determines the dialog styling
 * (icon color, button emphasis).
 */
class ComposeConfirmationProvider(
    private val context: Context,
) : IConfirmationProvider {

    override suspend fun requestConfirmation(
        request: ConfirmationRequest,
        timeoutMs: Long,
    ): ConfirmationResponse {
        // Wait for user response, defaulting to deny after timeout.
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val activity = getForegroundActivity()

                // Build the message
                val message = buildString {
                    appendLine(request.description)
                    appendLine()
                    appendLine("风险等级: ${riskLabel(request.riskLevel)}")
                    appendLine("节点类型: ${request.nodeType}")
                    if (request.details.isNotEmpty()) {
                        appendLine()
                        request.details.forEach { (k, v) ->
                            if (k != "node_type" && k != "risk_reasons") {
                                appendLine("$k: $v")
                            }
                        }
                    }
                    val reasons = request.details["risk_reasons"]
                    if (!reasons.isNullOrEmpty()) {
                        appendLine()
                        appendLine("风险提示: $reasons")
                    }
                }

                val builder = AlertDialog.Builder(activity ?: context)
                .setTitle("操作确认")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("允许") { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(
                            ConfirmationResponse(
                                approved = true,
                                oneTimeToken = request.oneTimeToken,
                            )
                        )
                    }
                }
                .setNegativeButton("拒绝") { _, _ ->
                    if (continuation.isActive) {
                        continuation.resume(
                            ConfirmationResponse(
                                approved = false,
                                oneTimeToken = request.oneTimeToken,
                            )
                        )
                    }
                }

            // Set icon/color based on risk level
            val dialog = builder.create().apply {
                setOnShowListener {
                    val positiveButton = getButton(AlertDialog.BUTTON_POSITIVE)
                    val negativeButton = getButton(AlertDialog.BUTTON_NEGATIVE)

                    when (request.riskLevel) {
                        RiskLevel.CRITICAL -> {
                            positiveButton.setTextColor(Color.RED)
                            // Change positive text for critical
                            positiveButton.text = "我知道风险，仍要执行"
                        }
                        RiskLevel.HIGH -> {
                            positiveButton.setTextColor(Color.parseColor("#FF6600"))
                        }
                        else -> {}
                    }
                }
            }

            dialog.show()

            // Handle cancellation (coroutine cancelled or timeout)
            continuation.invokeOnCancellation {
                dialog.dismiss()
            }
        }
        } ?: ConfirmationResponse(approved = false, oneTimeToken = request.oneTimeToken,
            userNote = "Confirmation timed out after ${timeoutMs}ms")
    }

    private fun riskLabel(risk: RiskLevel): String = when (risk) {
        RiskLevel.LOW -> "🟢 低"
        RiskLevel.MEDIUM -> "🟡 中"
        RiskLevel.HIGH -> "🟠 高"
        RiskLevel.CRITICAL -> "🔴 严重"
    }

    private fun getForegroundActivity(): Activity? {
        // This is a best-effort approach. In practice, the Activity reference
        // should be passed via the app's lifecycle management.
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getMethod("currentActivityThread")
                .invoke(null)
            val activitiesField = activityThreadClass.getDeclaredField("mActivities")
            activitiesField.isAccessible = true
            val activities = activitiesField.get(activityThread) as? Map<*, *>
            activities?.values?.firstOrNull()?.let {
                val activityRecord = it.javaClass
                val pausedField = activityRecord.getDeclaredField("paused")
                pausedField.isAccessible = true
                if (!(pausedField.getBoolean(it))) {
                    val activityField = activityRecord.getDeclaredField("activity")
                    activityField.isAccessible = true
                    activityField.get(it) as? Activity
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
