package com.ailun.habitat.app.debugger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ailun.habitat.trajectory.TrajectoryStore
import com.ailun.habitat.trajectory.TrajectoryStep

@Composable
fun AgentDebuggerScreen(
    runId: String?,
    trajectoryStore: TrajectoryStore,
    onClose: () -> Unit,
) {
    val runs = remember(trajectoryStore) { trajectoryStore.getRecentRuns(20) }
    var selectedRunId by remember(runId) { mutableStateOf(runId) }
    var selectedStep by remember { mutableStateOf<Int?>(null) }

    // Auto-select first run if none selected
    LaunchedEffect(runs, selectedRunId) {
        if (selectedRunId == null && runs.isNotEmpty()) {
            selectedRunId = runs.first().runId
        }
    }

    val steps = remember(selectedRunId) {
        selectedRunId?.let { trajectoryStore.getRun(it) } ?: emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Agent Debugger", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }

        // Run selector
        if (runs.size > 1) {
            TrajectoryTimelineView(
                runs = runs,
                selectedRunId = selectedRunId,
                onRunSelected = { selectedRunId = it; selectedStep = null },
            )
        }

        Row(modifier = Modifier.weight(1f)) {
            // Left: Step list
            LazyColumn(
                modifier = Modifier.width(200.dp).fillMaxHeight().background(Color(0xFF1A1A2E)),
            ) {
                items(steps) { step ->
                    StepListItem(
                        step = step,
                        isSelected = step.stepIndex == selectedStep,
                        onClick = { selectedStep = step.stepIndex },
                    )
                }
            }

            // Right: Detail panels
            Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                val currentStep = selectedStep?.let { idx -> steps.getOrNull(idx) }

                if (currentStep != null) {
                    VariableInspector(
                        variableDiff = currentStep.variableDiff,
                        modifier = Modifier.weight(0.4f),
                    )
                    ExpressionExplanationView(
                        expressions = currentStep.expressionEvaluations,
                        modifier = Modifier.weight(0.3f),
                    )
                    RiskLabelView(
                        riskLabels = currentStep.riskLabels,
                        modifier = Modifier.weight(0.3f),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a step to inspect", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepListItem(step: TrajectoryStep, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = when {
        isSelected -> Color(0xFF2A2A4E)
        !(step.nodeResult?.success ?: true) -> Color(0xFF4E2A2A)
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        color = bgColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            Text(
                "[${step.stepIndex}]",
                color = if (step.nodeResult?.success != false) Color(0xFF4CAF50) else Color(0xFFE53935),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                step.nodeType.removePrefix("ACTION_").removePrefix("CONDITION_"),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
