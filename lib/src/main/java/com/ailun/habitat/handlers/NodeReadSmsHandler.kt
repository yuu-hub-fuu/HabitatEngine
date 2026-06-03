package com.ailun.habitat.handlers

import android.provider.Telephony
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.api.IShellExecutor
import java.util.regex.Pattern

class NodeReadSmsHandler(
    private val shellExecutor: IShellExecutor? = null,
) : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val app = context.appContext
        val p = node.params
        val filterBy = p?.get("filter_by")?.toString()?.trim()?.lowercase() ?: FILTER_LATEST
        val senderFilter = p?.get("sender")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val contentFilter = p?.get("content")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val maxScan = ((p?.get("max_scan") as? Number)?.toInt() ?: 20).coerceIn(1, 500)
        val extractCode = when (val v = p?.get("extract_code")) {
            is Boolean -> v
            else -> v?.toString()?.equals("true", true) == true
        }

        // Strategy 1: ContentProvider (needs READ_SMS runtime permission)
        var cursor: android.database.Cursor? = null
        try {
            val sortOrder = "${Telephony.Sms.DATE} DESC"
            cursor = app.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null, sortOrder,
            )

            if (cursor != null) {
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                if (addressIdx >= 0 && bodyIdx >= 0) {
                    if (scanCursor(cursor, addressIdx, bodyIdx, maxScan, filterBy, senderFilter, contentFilter, extractCode, context)) {
                        return node.nextResult()
                    }
                }
            }
        } catch (e: SecurityException) {
            context.log("ReadSMS: SecurityException, falling back to shell")
        } catch (e: Exception) {
            context.log("ReadSMS: ${e.message}, falling back to shell")
        } finally {
            cursor?.close()
        }

        // Strategy 2: Shell via Shizuku (bypasses READ_SMS permission)
        val shell = shellExecutor
        if (shell != null) {
            context.log("ReadSMS: trying shell fallback...")
            val result = readSmsViaShell(shell, maxScan, filterBy, senderFilter, contentFilter, extractCode, context)
            if (result) return node.nextResult()
        }

        context.variables["sms_found"] = false
        context.variables["sms_error"] = "Cannot access SMS — grant READ_SMS or enable Shizuku"
        return node.nextResult()
    }

    private fun scanCursor(
        cursor: android.database.Cursor,
        addressIdx: Int,
        bodyIdx: Int,
        maxScan: Int,
        filterBy: String,
        senderFilter: String?,
        contentFilter: String?,
        extractCode: Boolean,
        context: WorkflowContext,
    ): Boolean {
        var scanned = 0
        while (cursor.moveToNext() && scanned < maxScan) {
            scanned++
            val sender = cursor.getString(addressIdx) ?: ""
            val body = cursor.getString(bodyIdx) ?: ""

            val code = if (extractCode) extractVerificationCode(body) else ""
            val isMatch = matches(sender, body, code, filterBy, senderFilter, contentFilter, extractCode)
            if (isMatch && (!extractCode || code.isNotEmpty())) {
                setResult(context, sender, body, code)
                return true
            }
        }
        return false
    }

    private suspend fun readSmsViaShell(
        shell: IShellExecutor,
        maxScan: Int,
        filterBy: String,
        senderFilter: String?,
        contentFilter: String?,
        extractCode: Boolean,
        context: WorkflowContext,
    ): Boolean {
        try {
            val output = shell.exec(
                "content query --uri content://sms/inbox --projection address:body:date --sort \"date DESC\"",
                asRoot = false,
            )
            if (output.startsWith("Error") || output.isBlank()) {
                context.log("ReadSMS shell: $output")
                return false
            }

            var scanned = 0
            for (line in output.lines()) {
                if (scanned >= maxScan) break
                val trimmed = line.trim()
                if (!trimmed.startsWith("Row:")) continue

                scanned++
                val sender = extractField(trimmed, "address")
                val body = extractField(trimmed, "body")
                if (sender.isEmpty() && body.isEmpty()) continue

                val code = if (extractCode) extractVerificationCode(body) else ""
                val isMatch = matches(sender, body, code, filterBy, senderFilter, contentFilter, extractCode)
                if (isMatch && (!extractCode || code.isNotEmpty())) {
                    setResult(context, sender, body, code)
                    context.log("ReadSMS shell: found verification code (${code.length} chars)")
                    return true
                }
            }
            context.log("ReadSMS shell: scanned $scanned messages, no match")
        } catch (e: Exception) {
            context.log("ReadSMS shell error: ${e.message}")
        }
        return false
    }

    private fun extractField(line: String, fieldName: String): String {
        // Parses "Row: N address=106xxxxx, body=xxx, date=xxx"
        val pattern = Pattern.compile("""$fieldName=([^,]*(?:,[^,]+)*?)(?:, \w+=|$)""")
        val m = pattern.matcher(line)
        return if (m.find()) m.group(1)?.trim() ?: "" else ""
    }

    private fun matches(
        sender: String, body: String, code: String,
        filterBy: String, senderFilter: String?, contentFilter: String?, extractCode: Boolean,
    ): Boolean {
        val senderMatches = senderFilter.isNullOrEmpty() || sender.contains(senderFilter, ignoreCase = true)
        val contentMatches = if (extractCode) {
            code.isNotEmpty()
        } else {
            contentFilter.isNullOrEmpty() || body.contains(contentFilter, ignoreCase = true)
        }
        return when (filterBy) {
            FILTER_LATEST -> true
            FILTER_SENDER -> senderMatches
            FILTER_CONTENT -> contentMatches
            FILTER_BOTH -> senderMatches && contentMatches
            else -> false
        }
    }

    private fun setResult(context: WorkflowContext, sender: String, body: String, code: String) {
        context.variables["sms_found"] = true
        context.variables["sms_sender"] = sender
        context.variables["sms_content"] = body
        context.variables["verification_code"] = code
    }

    private fun extractVerificationCode(text: String): String {
        val patterns = listOf(
            Pattern.compile("""(?<!\d)\d{4,8}(?!\d)"""),
            Pattern.compile("""\b[A-Za-z0-9]{6,12}\b"""),
        )
        for (p in patterns) {
            val m = p.matcher(text)
            if (m.find()) return m.group()
        }
        return ""
    }

    companion object {
        const val FILTER_LATEST = "latest"
        const val FILTER_SENDER = "sender"
        const val FILTER_CONTENT = "content"
        const val FILTER_BOTH = "both"
    }
}
