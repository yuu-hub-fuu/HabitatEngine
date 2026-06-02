package com.ailun.habitat.app.debugger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ailun.habitat.trajectory.RunSummary

@Composable
fun TrajectoryTimelineView(
    runs: List<RunSummary>,
    selectedRunId: String?,
    onRunSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        runs.forEach { run ->
            val isSelected = run.runId == selectedRunId
            val dotColor = when {
                run.success -> Color(0xFF4CAF50)
                run.errorCount > 0 -> Color(0xFFE53935)
                else -> Color.Gray
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onRunSelected(run.runId) },
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    run.taskDescription?.take(15) ?: run.runId.take(8),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White else Color.Gray,
                )
                Text(
                    "${run.stepCount} steps",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )
            }
        }
    }
}
