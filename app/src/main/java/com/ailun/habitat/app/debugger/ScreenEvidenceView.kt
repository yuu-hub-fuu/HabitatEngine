package com.ailun.habitat.app.debugger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ailun.habitat.perception.ScreenState

@Composable
fun ScreenEvidenceView(
    preState: ScreenState?,
    postState: ScreenState?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Screen Evidence", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Before state
                Column(modifier = Modifier.weight(1f)) {
                    Text("Before", color = Color(0xFF4FC3F7), style = MaterialTheme.typography.labelSmall)
                    if (preState != null) {
                        Text("${preState.packageName}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text("${preState.clickableElements.size} elements", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text("Text: ${preState.ocrResult?.fullText?.take(100) ?: "N/A"}", color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    } else {
                        Text("Not captured", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.width(8.dp))

                // After state
                Column(modifier = Modifier.weight(1f)) {
                    Text("After", color = Color(0xFF81C784), style = MaterialTheme.typography.labelSmall)
                    if (postState != null) {
                        Text("${postState.packageName}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text("${postState.clickableElements.size} elements", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        Text("Text: ${postState.ocrResult?.fullText?.take(100) ?: "N/A"}", color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    } else {
                        Text("Not captured", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
