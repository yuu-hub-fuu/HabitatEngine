package com.ailun.habitat.trajectory

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TrajectoryReplayer(private val store: TrajectoryStore) {
    fun replay(runId: String, replaySpeedMs: Long = 500L): Flow<TrajectoryStep> = flow {
        val steps = store.getRun(runId)
        for (step in steps) {
            emit(step)
            delay(replaySpeedMs)
        }
    }

    fun getStepCount(runId: String): Int = store.getRun(runId).size

    fun getStep(runId: String, stepIndex: Int): TrajectoryStep? =
        store.getRun(runId).getOrNull(stepIndex)
}
