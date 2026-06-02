package com.ailun.habitat.planir

import com.ailun.habitat.NodeHandlerFactory
import com.ailun.habitat.capability.CapabilityMapping
import com.ailun.habitat.expression.ExpressionEngine

class PlanIRValidator(
    private val expressionEngine: ExpressionEngine,
    private val factory: NodeHandlerFactory? = null,
) {
    fun validate(plan: PlanIR): PlanIRValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val fixes = mutableListOf<String>()

        if (plan.task.description.isBlank()) {
            errors.add("Task description is empty")
            fixes.add("Provide a clear task description")
        }

        if (plan.steps.isEmpty()) {
            errors.add("Plan has no steps")
            fixes.add("Add at least one step to the plan")
        }

        val stepIds = plan.steps.map { it.id }.toSet()
        for (step in plan.steps) {
            if (step.id.isBlank()) {
                errors.add("Step has blank id")
                continue
            }
            if (step.actionType.isBlank()) {
                errors.add("Step '${step.id}' has blank actionType")
                fixes.add("Specify a valid ACTION_* type for step '${step.id}'")
                continue
            }
            if (factory != null && factory.get(step.actionType) == null) {
                warnings.add("Step '${step.id}': actionType '${step.actionType}' has no registered handler")
                fixes.add("Register a handler for '${step.actionType}' or use a different actionType")
            }
            if (step.onFailure.startsWith("JUMP_TO(")) {
                val target = step.onFailure.removePrefix("JUMP_TO(").removeSuffix(")")
                if (target !in stepIds) {
                    errors.add("Step '${step.id}': JUMP_TO target '$target' does not exist")
                    fixes.add("Fix the JUMP_TO target or add the referenced step")
                }
            }
            step.preCondition?.let { validateCondition(it, step.id, errors, fixes) }
            step.postCondition?.let { validateCondition(it, step.id, errors, fixes) }
        }

        for (condition in plan.preconditions) validateCondition(condition, "precondition", errors, fixes)
        for (condition in plan.postconditions) validateCondition(condition, "postcondition", errors, fixes)

        val declaredCaps = plan.requiredCapabilities.toSet()
        val stepCaps = plan.steps.flatMap { CapabilityMapping.capabilitiesForNodeType(it.actionType) }
            .map { it.name }.toSet()
        val undeclared = stepCaps - declaredCaps
        if (undeclared.isNotEmpty()) {
            warnings.add("Undeclared capabilities: ${undeclared.joinToString()}")
            fixes.add("Add ${undeclared.joinToString()} to requiredCapabilities")
        }

        return PlanIRValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            suggestedFixes = fixes,
        )
    }

    private fun validateCondition(
        condition: Condition,
        context: String,
        errors: MutableList<String>,
        fixes: MutableList<String>,
    ) {
        if (condition.expression.isBlank()) {
            errors.add("$context: empty expression")
            return
        }
        try {
            expressionEngine.parse(condition.expression)
        } catch (e: Exception) {
            errors.add("$context: invalid expression '${condition.expression}': ${e.message}")
            fixes.add("Fix the expression syntax in $context")
        }
    }
}
