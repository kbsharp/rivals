package com.kevinbevan.rivals.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.ui.theme.RivalsTheme

@Composable
fun HomeScreen(
    onStartSession: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onStartSession = onStartSession,
        onOpenHistory = onOpenHistory,
        onOpenStats = onOpenStats,
        onSignOut = viewModel::signOut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onStartSession: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rivals") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.playerName.isNotEmpty()) {
                Text("Hi, ${uiState.playerName}", style = MaterialTheme.typography.bodyLarge)
            }
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
private fun HomeContentPreview() {
    RivalsTheme { HomeContent(HomeUiState("Kevin"), {}, {}, {}, {}) }
}
