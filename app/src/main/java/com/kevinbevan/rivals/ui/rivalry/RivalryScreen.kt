package com.kevinbevan.rivals.ui.rivalry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.session.DefaultMatchSettings
import com.kevinbevan.rivals.ui.session.MatchSettingsPicker
import com.kevinbevan.rivals.ui.theme.RivalsTheme

@Composable
fun RivalryScreen(
    onOpenSession: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenStats: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RivalryViewModel = viewModel(factory = RivalryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.openSessionId) {
        uiState.openSessionId?.let {
            viewModel.onSessionOpened()
            onOpenSession(it)
        }
    }
    LaunchedEffect(uiState.gone) { if (uiState.gone) onBack() }

    RivalryContent(
        uiState = uiState,
        onStartSession = viewModel::startSession,
        onResumeSession = onOpenSession,
        onOpenHistory = { onOpenHistory(viewModel.rivalryId) },
        onOpenStats = { onOpenStats(viewModel.rivalryId) },
        onRemove = viewModel::remove,
        onBack = onBack,
        onErrorShown = viewModel::dismissError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RivalryContent(
    uiState: RivalryUiState,
    onStartSession: (MatchSettings, String?) -> Unit,
    onResumeSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    onErrorShown: () -> Unit,
) {
    var choosingSettings by rememberSaveable { mutableStateOf(false) }
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
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
                title = { Text("You v ${uiState.rivalName}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.pendingSync) {
                        Icon(
                            painterResource(R.drawable.ic_cloud_upload),
                            contentDescription = "Waiting to sync",
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Remove rival") },
                            onClick = {
                                menuOpen = false
                                confirmingRemove = true
                            },
                        )
                    }
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
                PlayerTotal(uiState.rivalName, uiState.rivalWins, Modifier.weight(1f))
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
                        enabled = uiState.accepted,
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                    ) { Text("Start session", style = MaterialTheme.typography.titleMedium) }
                    if (!uiState.accepted) {
                        Text(
                            "${uiState.rivalName} needs to accept your invite before you can start a session.",
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
        NewSessionDialog(
            recentVenues = uiState.recentVenues,
            onStart = { settings, venue ->
                choosingSettings = false
                onStartSession(settings, venue)
            },
            onDismiss = { choosingSettings = false },
        )
    }
    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove ${uiState.rivalName}?") },
            text = { Text("You won't be able to start sessions together until one of you invites the other again. Past sessions stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRemove = false
                    onRemove()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmingRemove = false }) { Text("Cancel") } },
        )
    }
}

/** Match settings for the first match, plus an optional venue. */
@Composable
private fun NewSessionDialog(
    recentVenues: List<String>,
    onStart: (MatchSettings, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var settings by remember { mutableStateOf(DefaultMatchSettings) }
    var venue by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New session") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MatchSettingsPicker(settings, { settings = it })
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recentVenues.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentVenues.forEach { v ->
                            FilterChip(
                                selected = venue.trim().equals(v, ignoreCase = true),
                                onClick = { venue = v },
                                label = { Text(v) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(settings, venue.trim().ifEmpty { null }) }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
private fun RivalryContentPreview() {
    RivalsTheme {
        RivalryContent(
            RivalryUiState(loading = false, myName = "Kevin", rivalName = "Dave", myWins = 12, rivalWins = 9),
            { _, _ -> }, {}, {}, {}, {}, {}, {},
        )
    }
}
