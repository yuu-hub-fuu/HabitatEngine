package com.ailun.habitat.trajectory

import com.ailun.habitat.NodeResult
import com.ailun.habitat.execution.DiffEntry
import com.ailun.habitat.expression.ExpressionResult
import com.ailun.habitat.perception.ScreenState

data class TrajectoryStep(
    val runId: String,
    val stepIndex: Int,
    val taskDescription: String?,
    val nodeId: String,
    val nodeType: String,
    val actionParams: Map<String, Any>,
    val preScreenState: ScreenState?,
    val postScreenState: ScreenState?,
    val variableDiff: Map<String, DiffEntry>,
    val nodeResult: NodeResult?,
    val expressionEvaluations: List<ExpressionResult>,
    val riskLabels: List<String>,
    val confirmationDecisions: List<ConfirmationRecord>,
    val timestampMs: Long,
    val durationMs: Long,
)

data class ConfirmationRecord(
    val nodeId: String,
    val approved: Boolean,
    val userNote: String?,
    val timestampMs: Long,
)
