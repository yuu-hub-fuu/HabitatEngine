package com.ailun.habitat.strategy

import com.ailun.habitat.ai.ILLMService
import com.ailun.habitat.planir.*
import com.ailun.habitat.skill.SkillRegistry

class LLMPlannerService(private val llmService: ILLMService) : IPlanner {
    override suspend fun plan(task: TaskGoal, context: PlanningContext): PlanResult {
        if (!llmService.isReady()) {
            // Return a template-based plan
            return PlanResult(
                planIR = PlanIR(task = task, steps = emptyList()),
                confidence = 0.1f,
                planningRationale = "LLM not ready — empty plan returned. Load a model first.",
            )
        }

        val skills = context.availableSkills.ifEmpty { SkillRegistry.getAll() }

        val prompt = buildString {
            appendLine("You are a mobile automation planner. Given a task, produce a PlanIR.")
            appendLine()
            appendLine("Task: ${task.description}")
            if (task.app != null) appendLine("Target app: ${task.app}")
            if (task.entryPage != null) appendLine("Entry page: ${task.entryPage}")
            appendLine()
            appendLine("Current device state:")
            appendLine("  Package: ${context.deviceState.currentPackage}")
            appendLine("  Activity: ${context.deviceState.currentActivity}")
            appendLine()
            appendLine("Available skills: ${skills.joinToString { "${it.name}(${it.id})" }}")
            appendLine("Max steps: ${context.maxSteps}")
            appendLine()
            appendLine("Available action types: ACTION_CLICK, ACTION_SWIPE, ACTION_INPUT_TEXT, ACTION_LAUNCH_APP, ACTION_WAIT, CONDITION_SWITCH, ACTION_READ_SCREEN, ACTION_SCREENSHOT, ACTION_FIND_ELEMENT, ACTION_SHELL, ACTION_HTTP_REQUEST, ACTION_CALL_SKILL, ACTION_SET_VARIABLE, ACTION_LOG, ACTION_TOAST")
            appendLine()
            appendLine("Respond with a PlanIR JSON:")
            appendLine("{")
            appendLine("  \"task\": {\"description\": \"...\", \"app\": \"...\"},")
            appendLine("  \"steps\": [")
            appendLine("    {\"id\": \"step1\", \"intent\": \"...\", \"actionType\": \"...\", \"params\": {...}}")
            appendLine("  ],")
            appendLine("  \"riskLevel\": \"LOW|MEDIUM|HIGH|CRITICAL\",")
            appendLine("  \"failureRecovery\": \"ABORT|RETRY_ONCE|SKIP_AND_CONTINUE\"")
            appendLine("}")
        }

        val response = llmService.chat(prompt)

        return try {
            // Parse LLM output as PlanIR
            val planIR = parseLLMPlanIR(response, task)
            PlanResult(
                planIR = planIR,
                confidence = 0.7f,
                planningRationale = "LLM-generated plan with ${planIR.steps.size} steps",
            )
        } catch (e: Exception) {
            PlanResult(
                planIR = PlanIR(task = task, steps = emptyList()),
                confidence = 0.0f,
                planningRationale = "Failed to parse LLM plan output: ${e.message}",
            )
        }
    }

    private fun parseLLMPlanIR(response: String, task: TaskGoal): PlanIR {
        // Try to extract JSON from the response (it might be wrapped in markdown)
        val jsonStr = response
            .substringAfter("```json", response)
            .substringBefore("```", response)
            .trim()
            .ifEmpty { response }

        val json = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
        val stepsArray = json.getAsJsonArray("steps") ?: com.google.gson.JsonArray()

        val steps = stepsArray.map { stepEl ->
            val s = stepEl.asJsonObject
            val paramsMap = mutableMapOf<String, Any>()
            s.getAsJsonObject("params")?.entrySet()?.forEach { (k, v) ->
                paramsMap[k] = when {
                    v.isJsonPrimitive -> {
                        val p = v.asJsonPrimitive
                        when { p.isNumber -> p.asDouble; p.isBoolean -> p.asBoolean; else -> p.asString }
                    }
                    else -> v.toString()
                }
            }
            PlanStep(
                id = s.get("id")?.asString ?: "step_${stepsArray.size()}",
                intent = s.get("intent")?.asString ?: "",
                actionType = s.get("actionType")?.asString ?: "ACTION_LOG",
                params = paramsMap,
                requireConfirmation = s.get("requireConfirmation")?.asBoolean ?: false,
            )
        }

        // Validate risk level string, default to LOW
        val riskLevelStr = try {
            val raw = json.get("riskLevel")?.asString ?: "LOW"
            com.ailun.habitat.capability.RiskLevel.valueOf(raw).name
        } catch (_: Exception) { "LOW" }

        return PlanIR(
            task = task,
            riskLevel = riskLevelStr,
            failureRecovery = json.get("failureRecovery")?.asString ?: "ABORT",
            steps = steps,
        )
    }
}
