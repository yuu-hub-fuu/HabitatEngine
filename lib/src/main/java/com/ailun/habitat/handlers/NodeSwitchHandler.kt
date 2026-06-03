package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.expression.ExpressionEngine
import com.ailun.habitat.expression.IVariableProvider

/**
 * [ACTION_SWITCH] — Multi-branch routing based on a variable's value.
 *
 * Unlike [CONDITION_SWITCH] (binary true/false), this dispatches to an
 * arbitrary number of branches where each branch key matches a variable value.
 *
 * params:
 * - `variable` (required): Context variable name to inspect
 * - `cases` (optional): JSON map of { "value": "node_id", ... }
 * - `default` (optional): Fallback branch key name in `branches`
 *
 * branches: Each key is a possible variable value. The matching branch's value
 * is the target node ID. If no match, `branches["default"]` is used. If that
 * also doesn't exist, execution stops.
 *
 * Example:
 * ```
 * {
 *   "id": "sw", "type": "ACTION_SWITCH",
 *   "params": { "variable": "status" },
 *   "branches": { "ok": "next_ok", "error": "on_err", "pending": "retry", "default": "on_unknown" }
 * }
 * ```
 */
class NodeSwitchHandler(
    private val expressionEngine: ExpressionEngine,
) : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: emptyMap()
        val varName = params["variable"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        if (varName == null) {
            context.log("ACTION_SWITCH: missing 'variable' parameter")
            return node.nextResult()
        }

        val actualValue = context.getVariable(varName)?.toString() ?: ""
        context.log("ACTION_SWITCH: variable '$varName' = '$actualValue'")

        // Try exact branch key match first
        val branches = node.branches ?: emptyMap()
        val matched = branches[actualValue]
        if (matched != null) {
            context.log("ACTION_SWITCH: exact match '$actualValue' → '$matched'")
            context.variables["switch_matched"] = actualValue
            context.variables["switch_success"] = true
            return matched
        }

        // Try expression-based match via inline case map in params
        val cases = params["cases"] as? Map<*, *>
        if (cases != null) {
            for ((caseKey, caseTarget) in cases) {
                val expr = caseKey.toString()
                if (expr == actualValue) {
                    val target = caseTarget.toString()
                    context.log("ACTION_SWITCH: case match '$expr' → '$target'")
                    context.variables["switch_matched"] = expr
                    context.variables["switch_success"] = true
                    return target
                }
            }
        }

        // Fallback to "default" branch
        val defaultTarget = params["default"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: branches["default"]
        if (defaultTarget != null) {
            context.log("ACTION_SWITCH: no match for '$actualValue', using default → '$defaultTarget'")
            context.variables["switch_matched"] = "default"
            context.variables["switch_success"] = true
            return defaultTarget
        }

        context.log("ACTION_SWITCH: no match for '$actualValue' and no default branch")
        context.variables["switch_success"] = false
        context.variables["_last_error"] = true
        context.variables["_last_error_msg"] = "Switch: no match for '$actualValue'"
        return null
    }
}
