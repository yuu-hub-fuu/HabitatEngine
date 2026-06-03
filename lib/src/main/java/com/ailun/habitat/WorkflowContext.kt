package com.ailun.habitat

import android.content.Context
import android.util.Log
import com.ailun.habitat.execution.DiffEntry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 工作流执行上下文。
 * 每个工作流实例拥有独立的 WorkflowContext。
 * variables 使用 ConcurrentHashMap 保证线程安全。
 * log() 输出时会附带当前工作流实例的唯一 ID (workflowId)，以便区分并发执行。
 *
 * @param context Android Context
 * @param definitionId 业务工作流定义 ID（HabitatWorkflow.id），用于日志归属
 * @param workflowId 本次执行实例的 UUID，用于并发隔离
 */
class WorkflowContext(
    context: Context,
    /** 业务工作流定义 ID — 与 HabitatWorkflow.id 一致，同一工作流多次执行共享此 ID。 */
    val definitionId: String? = null,
    /** 本次运行实例的唯一 UUID — 每次执行不同。 */
    val workflowId: String = UUID.randomUUID().toString(),
    /**
     * 严格模式：未定义变量直接报错（设置 _last_error 并抛异常），
     * 而不是被替换成字符串 "null"。默认 true。
     */
    private val strictInterpolation: Boolean = true,
) {
    val appContext: Context = context.applicationContext

    /** 线程安全的变量存储 */
    val variables: MutableMap<String, Any?> = ConcurrentHashMap()

    /** 日志回调，由 Executor 设置 */
    var onLog: ((String) -> Unit)? = null

    fun putVariable(key: String, value: Any?) { variables[key] = value }
    fun getVariable(key: String): Any? = variables[key]

    /**
     * 将字符串中的 `${var_name}` 替换为变量的实际值。
     *
     * 变量名支持：
     * - 简单变量名：`${foo}`, `${bar123}`
     * - 点分路径：`${user.name}`, `${data.items[0].title}`
     *
     * **严格模式**（默认）：如果变量未定义，设置 `_last_error` 且抛 [MissingVariableException]。
     * **非严格模式**：未定义变量替换成 `"null"` 字符串（向后兼容）。
     */
    fun interpolate(text: String): String {
        if (!text.contains("\${")) return text

        var result = text
        // Matches ${anything-here} including dots, hyphens, brackets
        val regex = """\$\{([^}]+)}""".toRegex()
        var pass = 0
        var anyMissing = false

        while (result.contains("\${") && pass < 5) {
            pass++
            result = regex.replace(result) { match ->
                val varName = match.groupValues[1].trim()
                val value = resolveVariable(varName)
                if (value == null && strictInterpolation) {
                    anyMissing = true
                    variables["_last_error"] = true
                    variables["_last_error_msg"] = "Undefined variable '\${$varName}'"
                    // Keep the unresolved token in the string so it's visible in error output.
                    match.value
                } else {
                    value?.toString() ?: "null"
                }
            }
        }

        if (anyMissing && strictInterpolation) {
            val missingMessage = "Missing variables in interpolation: " +
                regex.findAll(text).mapNotNull { m ->
                    val vn = m.groupValues[1].trim()
                    if (resolveVariable(vn) == null) "\${$vn}" else null
                }.joinToString(", ")
            throw MissingVariableException(missingMessage)
        }

        return result
    }

    /**
     * Resolve a variable name that may contain dots and brackets for deep property access.
     *
     * Resolution order:
     * 1. Exact key match in variables map
     * 2. Dot-path navigation: "user.name" → context.variables["user"].name
     * 3. Bracket access: "items[0]" → context.variables["items"][0]
     * 4. Mixed: "data.items[0].title"
     */
    private fun resolveVariable(varName: String): Any? {
        // 1. Exact key match (covers most cases including hyphens in keys)
        if (varName in variables) return variables[varName]

        // 2. If no dot/bracket, not found
        if (!varName.contains('.') && !varName.contains('[')) return null

        // 3. Navigate dot/bracket path starting from root variable
        return try {
            val rootKey = varName.substringBefore('.').substringBefore('[')
            var current: Any? = variables[rootKey] ?: return null
            val rest = varName.substring(rootKey.length)
            navigateSimplePath(current, rest)
        } catch (_: Exception) {
            null
        }
    }

    /** Lightweight path navigator for Kotlin Maps, Lists, and JSON objects. */
    private fun navigateSimplePath(root: Any?, path: String): Any? {
        var current = root
        val sb = StringBuilder()
        var i = 0
        while (i < path.length) {
            when (val ch = path[i]) {
                '.' -> {
                    if (sb.isNotEmpty()) {
                        current = accessField(current, sb.toString()) ?: return null
                        sb.clear()
                    }
                }
                '[' -> {
                    if (sb.isNotEmpty()) {
                        current = accessField(current, sb.toString()) ?: return null
                        sb.clear()
                    }
                    val close = path.indexOf(']', i + 1)
                    if (close < 0) return null
                    val idx = path.substring(i + 1, close).toIntOrNull() ?: return null
                    current = accessIndex(current, idx) ?: return null
                    i = close
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty()) {
            current = accessField(current, sb.toString()) ?: return null
        }
        return current
    }

    private fun accessField(obj: Any?, field: String): Any? = when (obj) {
        is Map<*, *> -> (obj as Map<String, Any?>)[field]
        is org.json.JSONObject -> if (obj.has(field)) obj.get(field) else null
        else -> {
            try {
                val fieldObj = obj?.javaClass?.getDeclaredField(field)
                fieldObj?.isAccessible = true
                fieldObj?.get(obj)
            } catch (_: Exception) { null }
        }
    }

    private fun accessIndex(obj: Any?, idx: Int): Any? = when (obj) {
        is List<*> -> obj.getOrNull(idx)
        is Array<*> -> obj.getOrNull(idx)
        is org.json.JSONArray -> if (idx < obj.length()) obj.get(idx) else null
        is Map<*, *> -> obj[idx.toString()]
        else -> null
    }

    /**
     * 打印工作流日志，经过脱敏处理后输出到 Logcat 和 UI 回调。
     */
    fun log(message: String) {
        val shortId = workflowId.take(8)
        val label = definitionId?.let { "[$it/$shortId]" } ?: "[$shortId]"
        val taggedMessage = "$label $message"
        val safe = LogSanitizer.redact(taggedMessage)
        Log.i("Habitat", safe)
        onLog?.invoke(safe)
    }

    /** Snapshot all current variable values. */
    fun snapshotVariables(): Map<String, Any?> = HashMap(variables)

    /** Diff between a previous snapshot and current state. */
    fun diffSnapshot(before: Map<String, Any?>): Map<String, DiffEntry> {
        val diffs = mutableMapOf<String, DiffEntry>()
        val after = variables
        for ((k, v) in after) {
            val beforeVal = before[k]
            if (beforeVal != v || k !in before) diffs[k] = DiffEntry(beforeVal, v)
        }
        for (k in before.keys) {
            if (k !in after) diffs[k] = DiffEntry(before[k], null)
        }
        return diffs
    }

    class MissingVariableException(message: String) : RuntimeException(message)
}

/**
 * Central log sanitizer — redacts sensitive information before it reaches Logcat/UI.
 *
 * Only used by [WorkflowContext.log()]; individual handlers may still write raw
 * data to [android.util.Log] directly, but UI-facing logs always pass through here.
 */
object LogSanitizer {
    /** Patterns in order of priority (token-like patterns first to avoid partial match). */
    private val SENSITIVE_PATTERNS = listOf<Pair<Regex, String>>(
        // Authorization headers / Bearer tokens
        Regex("""Authorization:\s*Bearer\s+\S+""", RegexOption.IGNORE_CASE) to "Authorization: Bearer ***",
        Regex("""Bearer\s+\S+""") to "Bearer ***",
        // OAuth / API tokens (key=value pattern with known token field names)
        Regex("""(token|api_key|apiKey|apikey|secret|password|passwd)\s*[:=]\s*\S+""", RegexOption.IGNORE_CASE) to "$1=***",
        // Cookies
        Regex("""[Cc]ookie:\s*[^;]+""") to "Cookie: ***",
        // Phone numbers (Chinese mobile: 1[3-9]XXXXXXXXX)
        Regex("""1[3-9]\d{9}""") to "138****0000",
        // Verification codes (4-8 digit codes frequently logged nearby "code"/"验证码")
        // Only redact when preceded by context words to avoid false positives
        Regex("""(?:code|验证码|验证|验证码是)[:= ]*(\d{4,8})""") { mr -> mr.value.replace(mr.groupValues[1], "****") },
        // Email addresses
        Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""") to "user@***.***",
        // File paths under /data/ or /sdcard/Android
        Regex("""(/sdcard/Android/[^\s,;]+)""") to "/sdcard/Android/***",
        Regex("""(/data/data/[^\s,;]+)""") to "/data/data/***",
        Regex("""(/data/user/[^\s,;]+)""") to "/data/user/***",
    )

    fun redact(message: String): String {
        var result = message
        for ((pattern, replacement) in SENSITIVE_PATTERNS) {
            result = when (replacement) {
                is String -> pattern.replace(result, replacement)
                is (MatchResult) -> String -> pattern.replace(result, replacement)
                else -> result
            }
        }
        return result
    }
}
