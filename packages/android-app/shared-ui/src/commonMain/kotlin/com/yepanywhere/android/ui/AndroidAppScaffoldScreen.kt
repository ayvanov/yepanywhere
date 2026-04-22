package com.yepanywhere.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yepanywhere.android.core.AndroidAppPlan

@Composable
fun AndroidAppScaffoldScreen(
    plan: AndroidAppPlan,
    dataLayerSummary: String,
) {
    AndroidAppTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Yep Anywhere Android",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = plan.summary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item {
                    SummaryCard(
                        title = "Data layer",
                        items = listOf(dataLayerSummary),
                    )
                }
                item {
                    SummaryCard(
                        title = "Modules",
                        items = plan.modules,
                    )
                }
                item {
                    SummaryCard(
                        title = "Supervisor MVP scope",
                        items = plan.scope,
                    )
                }
            }
        }
    }
}

@Composable
fun AndroidAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun SummaryCard(
    title: String,
    items: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )

            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "- $item",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

