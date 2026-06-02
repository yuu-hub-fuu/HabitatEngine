package com.ailun.habitat.app.debugger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ailun.habitat.expression.ExpressionResult

@Composable
fun ExpressionExplanationView(
    expressions: List<ExpressionResult>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Expression Evaluations", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))

            if (expressions.isEmpty()) {
                Text("No expressions evaluated", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn {
                    items(expressions) { result ->
                        val textColor = if (result.booleanResult) Color(0xFF4CAF50) else Color(0xFFE53935)
                        Text(
                            result.explanation,
                            color = textColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                        if (result.operator != null) {
                            Text(
                                "  → ${result.operator.symbol} | left=${result.leftValue}, right=${result.rightValue}",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
