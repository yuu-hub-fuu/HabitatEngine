package com.ailun.habitat.app.debugger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ailun.habitat.execution.DiffEntry

@Composable
fun VariableInspector(
    variableDiff: Map<String, DiffEntry>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Variables", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            if (variableDiff.isEmpty()) {
                Text("No changes", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn {
                    items(variableDiff.entries.toList()) { (key, diff) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(key, color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text(
                                "${diff.before ?: "∅"} → ${diff.after ?: "∅"}",
                                color = Color(0xFFB0BEC5),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
