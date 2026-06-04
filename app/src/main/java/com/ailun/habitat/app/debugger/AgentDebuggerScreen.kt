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
import com.ailun.habitat.TrajectoryStore
import com.ailun.habitat.TrajectoryStep

@Composable
fun AgentDebuggerScreen(
    runId: String?,
    onClose: () -> Unit,
) {
    val steps = remember { TrajectoryStore.getRecent(100) }
    var selectedStep by remember { mutableStateOf<Int?>(null) }

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

        if (steps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No trajectory data yet", color = Color.Gray)
            }
        } else {
            Row(modifier = Modifier.weight(1f)) {
                // Left: Step list
                LazyColumn(
                    modifier = Modifier.width(220.dp).fillMaxHeight().background(Color(0xFF1A1A2E)),
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
                    val current = selectedStep?.let { idx -> steps.find { it.stepIndex == idx } }

                    if (current != null) {
                        StepDetailView(current, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select a step to inspect", color = Color.Gray)
                        }
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
        !step.success -> Color(0xFF4E2A2A)
        else -> Color.Transparent
    }

    Surface(onClick = onClick, color = bgColor, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp)) {
            Text(
                "[${step.stepIndex}]",
                color = if (step.success) Color(0xFF4CAF50) else Color(0xFFE53935),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                step.nodeType.removePrefix("ACTION_").removePrefix("CONDITION_"),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StepDetailView(step: TrajectoryStep, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Node: ${step.nodeId}", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text("Type: ${step.nodeType}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text(
                "Result: ${if (step.success) "SUCCESS" else "FAILED"}",
                color = if (step.success) Color(0xFF4CAF50) else Color(0xFFE53935),
                style = MaterialTheme.typography.bodySmall,
            )
            step.errorMessage?.let {
                Text("Error: $it", color = Color(0xFFE53935), style = MaterialTheme.typography.bodySmall)
            }
            step.nextNodeId?.let {
                Text("Next: $it", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("Variables", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(step.variablesSnapshot.entries.toList()) { (k, v) ->
                    Text(
                        "  $k = ${v?.toString()?.take(100)}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
