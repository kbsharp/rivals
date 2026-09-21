package com.kevinbevan.rivals.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.session.DefaultMatchSettings
import com.kevinbevan.rivals.ui.session.MatchSettingsDialog
import com.kevinbevan.rivals.ui.theme.RivalsTheme

@Composable
fun HomeScreen(
    onOpenSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.openSessionId) {
        uiState.openSessionId?.let {
            viewModel.onSessionOpened()
            onOpenSession(it)
        }
    }

    HomeContent(
        uiState = uiState,
        onStartSession = viewModel::startSession,
        onResumeSession = onOpenSession,
        onOpenHistory = onOpenHistory,
        onOpenStats = onOpenStats,
        onSignOut = viewModel::signOut,
        onErrorShown = viewModel::dismissError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onStartSession: (MatchSettings) -> Unit,
    onResumeSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onSignOut: () -> Unit,
    onErrorShown: () -> Unit,
) {
    var choosingSettings by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rivals") },
                actions = {
                    if (uiState.pendingSync) {
                        Icon(
                            painterResource(R.drawable.ic_cloud_upload),
                            contentDescription = "Waiting to sync",
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.loading) {
                CircularProgressIndicator()
                return@Column
            }

            Text("Head to head", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerTotal(uiState.myName, uiState.myWins, Modifier.weight(1f))
                Text("–", style = MaterialTheme.typography.displayMedium)
                PlayerTotal(uiState.rivalName ?: "Rival", uiState.rivalWins, Modifier.weight(1f))
            }
            Text(
                "matches won",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val active = uiState.activeSession
            when {
                active != null -> Button(
                    onClick = { onResumeSession(active.id) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Resume session", style = MaterialTheme.typography.titleMedium)
                        Text("Tonight ${active.myWins} – ${active.rivalWins}")
                    }
                }
                uiState.starting -> CircularProgressIndicator(Modifier.size(48.dp))
                else -> {
                    Button(
                        onClick = { choosingSettings = true },
                        enabled = uiState.rivalId != null,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) { Text("Start session", style = MaterialTheme.typography.titleMedium) }
                    if (uiState.rivalId == null) {
                        Text(
                            "Your rival needs to sign in to Rivals once before you can start a session.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text("History") }
            OutlinedButton(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) { Text("Stats") }
        }
    }

    if (choosingSettings) {
        MatchSettingsDialog(
            title = "New session",
            confirmLabel = "Start",
            initial = DefaultMatchSettings,
            onConfirm = {
                choosingSettings = false
                onStartSession(it)
            },
            onDismiss = { choosingSettings = false },
        )
    }
}

@Composable
private fun PlayerTotal(name: String, wins: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(wins.toString(), style = MaterialTheme.typography.displayLarge)
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Preview
@Composable
private fun HomeContentPreview() {
    RivalsTheme {
        HomeContent(
            HomeUiState(loading = false, myName = "Kevin", rivalId = "b", rivalName = "Dave", myWins = 12, rivalWins = 9),
            {}, {}, {}, {}, {}, {},
        )
    }
}
