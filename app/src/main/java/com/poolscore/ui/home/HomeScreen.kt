package com.poolscore.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.poolscore.ui.theme.PoolScoreTheme

@Composable
fun HomeScreen(
    onStartSession: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Head to head", style = MaterialTheme.typography.titleMedium)
            // TODO(milestone 4): all-time record from Firestore.
            Text("0 – 0", style = MaterialTheme.typography.displayLarge)
            Button(
                onClick = onStartSession,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Start session") }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text("History") }
            OutlinedButton(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) { Text("Stats") }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    PoolScoreTheme { HomeScreen({}, {}, {}) }
}
