package com.ailun.habitat.success

import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import com.ailun.habitat.expression.ExpressionEngine
import com.ailun.habitat.expression.IVariableProvider

class SuccessEvaluator(
    private val expressionEngine: ExpressionEngine,
) {
    suspend fun evaluate(criteria: SuccessCriteria, context: WorkflowContext): SuccessEvaluation {
        val failed = mutableListOf<FailedCondition>()
        val log = StringBuilder()
        log.appendLine("Evaluating ${criteria.conditions.size} success criteria (requireAll=${criteria.requireAll}):")

        for (cond in criteria.conditions) {
            val result = evaluateCondition(cond, context)
            log.appendLine("  ${cond.type}: '${cond.expression}' → ${if (result) "PASS" else "FAIL"}")
            if (!result) {
                failed.add(FailedCondition(cond, "Expression '${cond.expression}' evaluated to false"))
            }
        }

        val passed = if (criteria.requireAll) failed.isEmpty() else failed.size < criteria.conditions.size

        return SuccessEvaluation(
            passed = passed,
            failedConditions = failed,
            evaluationLog = log.toString(),
        )
    }

    suspend fun evaluateNodePostCondition(node: WorkflowNode, context: WorkflowContext): SuccessEvaluation? {
        val raw = node.postCondition ?: return null

        val typeStr = raw["type"]?.toString() ?: return null
        val expr = raw["expression"]?.toString() ?: return null
        val type = try { SuccessConditionType.valueOf(typeStr) } catch (_: Exception) { return null }

        val condition = SuccessCondition(type = type, expression = expr)
        val criteria = SuccessCriteria(listOf(condition), requireAll = true)
        return evaluate(criteria, context)
    }

    private suspend fun evaluateCondition(cond: SuccessCondition, context: WorkflowContext): Boolean {
        return when (cond.type) {
            SuccessConditionType.VARIABLE_SATISFIES -> {
                val provider = object : IVariableProvider {
                    override fun getVariable(key: String): Any? = context.getVariable(key)
                }
                expressionEngine.evaluate(cond.expression, provider).booleanResult
            }
            SuccessConditionType.SCREEN_CONTAINS_TEXT -> {
                // Check if any screen-reading variable contains the text
                val screenText = listOfNotNull(
                    context.getVariable("screen_data")?.toString(),
                    context.getVariable("screen_text")?.toString(),
                    context.getVariable("ocr_text")?.toString(),
                ).joinToString(" ")
                screenText.contains(cond.expression, ignoreCase = true)
            }
            SuccessConditionType.FILE_EXISTS_WITH_CONTENT -> {
                val filePath = context.interpolate(cond.expression)
                val file = java.io.File(filePath)
                file.exists() && file.isFile && file.length() > 0
            }
            SuccessConditionType.HTTP_STATUS_MATCHES -> {
                val statusCode = context.getVariable("http_status_code")?.toString()?.toIntOrNull() ?: 0
                val expected = cond.expression.removePrefix("status == ").toIntOrNull() ?: 200
                statusCode in 200..399 || statusCode == expected
            }
            SuccessConditionType.ELEMENT_VISIBLE -> {
                context.getVariable("element_found")?.toString()?.toBoolean() ?: false
            }
        }
    }
}
