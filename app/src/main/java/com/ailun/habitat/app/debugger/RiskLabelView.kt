package com.ailun.habitat.app.debugger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RiskLabelView(
    riskLabels: List<String>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Risk Labels", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            if (riskLabels.isEmpty()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("No risks detected", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                riskLabels.forEach { label ->
                    val (riskColor, riskLevel) = when {
                        label.contains("CRITICAL", ignoreCase = true) || label.contains("严重", ignoreCase = true) ->
                            Pair(Color(0xFF000000), "CRITICAL")
                        label.contains("HIGH", ignoreCase = true) || label.contains("高", ignoreCase = true) ||
                        label.contains("delete", ignoreCase = true) || label.contains("删除", ignoreCase = true) ->
                            Pair(Color(0xFFE53935), "HIGH")
                        label.contains("MEDIUM", ignoreCase = true) || label.contains("中", ignoreCase = true) ->
                            Pair(Color(0xFFFF9800), "MEDIUM")
                        else -> Pair(Color(0xFF4CAF50), "LOW")
                    }

                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(riskColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "[$riskLevel]",
                            color = riskColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            label,
                            color = Color(0xFFB0BEC5),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
