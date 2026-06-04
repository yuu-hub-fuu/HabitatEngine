package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.RuntimeVars
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_REGEX] — Extract substrings via regular expression.
 *
 * params:
 * - `input` (required): Source text. Supports `${var}` interpolation.
 * - `pattern` (required): Java regex pattern.
 * - `group` (optional): Capture group index (0 = full match, 1 = first group).
 *   Default 0. Supports named groups via `named_group`.
 * - `named_group` (optional): Named capture group. Takes precedence over `group`.
 * - `output_var` (optional): Variable name to store the result. Default "regex_result".
 *
 * Output variables:
 * - `regex_success` (Boolean)
 * - `regex_result` / `output_var` (String): matched text
 * - `regex_match_count` (Int): number of matches found
 */
class NodeRegexHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: emptyMap()

        val rawInput = params["input"]?.toString() ?: ""
        if (rawInput.isEmpty()) {
            return NodeResult.failure(node.branches?.get("error") ?: node.next,
                "Missing 'input' parameter",
                mapOf("regex_success" to false, "regex_error" to "Missing 'input' parameter"))
        }
        val input = try { context.interpolate(rawInput) }
            catch (_: WorkflowContext.MissingVariableException) { rawInput }

        val patternStr = params["pattern"]?.toString() ?: ""
        if (patternStr.isEmpty()) {
            return NodeResult.failure(node.branches?.get("error") ?: node.next,
                "Missing 'pattern' parameter",
                mapOf("regex_success" to false, "regex_error" to "Missing 'pattern' parameter"))
        }

        val outputVar = params["output_var"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "regex_result"
        val flags = if (params["case_insensitive"]?.toString()?.equals("true", true) == true)
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        else setOf(RegexOption.MULTILINE)

        val regex = try {
            patternStr.toRegex(flags)
        } catch (e: Exception) {
            return NodeResult.failure(node.branches?.get("error") ?: node.next,
                "Invalid regex: ${e.message}",
                mapOf("regex_success" to false, "regex_error" to "Invalid regex: ${e.message}"))
        }

        val matches = regex.findAll(input).toList()

        if (matches.isEmpty()) {
            context.log("REGEX: pattern '$patternStr' no match in input (${input.length} chars)")
            return NodeResult.success(node.next, mapOf(
                outputVar to "", "regex_result" to "",
                "regex_success" to false, "regex_match_count" to 0,
            ))
        }

        // Priority: named_group > group index
        val namedGroup = params["named_group"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val groupIdx = (params["group"] as? Number)?.toInt() ?: 0
        val firstMatch = matches.first()

        val extracted = if (namedGroup != null) {
            firstMatch.groups[namedGroup]?.value ?: ""
        } else {
            firstMatch.groupValues.getOrElse(groupIdx) { firstMatch.value }
        }

        context.log("REGEX: pattern '$patternStr' → extracted '${extracted.take(60)}' (${matches.size} matches)")
        return NodeResult.success(node.next, mapOf(
            outputVar to extracted, "regex_result" to extracted,
            "regex_success" to true, "regex_match_count" to matches.size,
        ))
    }
}
