package com.ailun.habitat.strategy

import com.ailun.habitat.explorer.AppMap
import com.ailun.habitat.perception.ScreenState
import com.ailun.habitat.planir.PlanIR
import com.ailun.habitat.planir.TaskGoal
import com.ailun.habitat.skill.SkillDefinition

interface IPlanner {
    suspend fun plan(task: TaskGoal, context: PlanningContext): PlanResult
}

data class PlanningContext(
    val availableSkills: List<SkillDefinition> = emptyList(),
    val appMap: AppMap? = null,
    val deviceState: DeviceState = DeviceState(),
    val maxSteps: Int = 30,
)

data class DeviceState(
    val currentPackage: String = "",
    val currentActivity: String = "",
    val screenState: ScreenState? = null,
)

data class PlanResult(
    val planIR: PlanIR,
    val confidence: Float = 1.0f,
    val alternatives: List<PlanIR> = emptyList(),
    val planningRationale: String = "",
)
