package com.ailun.habitat.handlers

import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode

/**
 * [ACTION_FOR_EACH] — iterate over an array variable.
 *
 * params:
 * - `array_var` (required): name of the context variable containing a list/array
 * - `item_var` (optional, default "item"): variable name for current item on each iteration
 * - `index_var` (optional, default "index"): variable name for current index on each iteration
 * - `body_node` (required): node ID to jump to for each iteration
 *
 * branches:
 * - `done` (optional): node to jump to after all items are processed
 */
class NodeForEachHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.success(node.next)

        val arrayVar = params["array_var"]?.toString()?.trim().orEmpty()
        val itemVar = params["item_var"]?.toString()?.trim() ?: "item"
        val indexVar = params["index_var"]?.toString()?.trim() ?: "index"
        val bodyNode = params["body_node"]?.toString()?.trim()

        // Resolve the array
        val arrayObj = context.getVariable(arrayVar)
        val list = when (arrayObj) {
            is List<*> -> arrayObj
            is Array<*> -> arrayObj.toList()
            is Collection<*> -> arrayObj.toList()
            else -> return handleNonArray(node, context, arrayVar, arrayObj)
        }

        // Track iteration state
        val iterStateKey = "_for_each_state_${node.id?.trim().orEmpty()}"
        val currentIndex = (context.getVariable(iterStateKey) as? Number)?.toInt() ?: 0

        if (currentIndex >= list.size) {
            // All items processed — reset state and go to done/finished
            context.putVariable(iterStateKey, 0)
            context.putVariable(itemVar, null)
            context.putVariable(indexVar, list.size)
            val doneTarget = node.branches?.get("done") ?: node.next
            context.log("ForEach: done (${list.size} items)")
            return NodeResult.success(doneTarget)
        }

        // Set current item and advance index
        val item = list[currentIndex]
        context.putVariable(itemVar, item)
        context.putVariable(indexVar, currentIndex)
        context.putVariable(iterStateKey, currentIndex + 1)

        context.log("ForEach: [$currentIndex/${list.size}] $itemVar=$item → $bodyNode")
        return NodeResult.success(bodyNode ?: node.next)
    }

    private fun handleNonArray(
        node: WorkflowNode,
        context: WorkflowContext,
        varName: String,
        value: Any?,
    ): NodeResult {
        val msg = if (varName.isEmpty()) {
            "FOR_EACH requires 'array_var' parameter"
        } else {
            "FOR_EACH: '$varName' is not an array (got: ${value?.javaClass?.simpleName ?: "null"})"
        }
        context.log(msg)
        return NodeResult.failure(
            next = node.branches?.get("error") ?: node.branches?.get("done") ?: node.next,
            error = msg,
        )
    }
}
