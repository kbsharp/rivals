package com.kevinbevan.rivals.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.time.Instant

@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(uiState, onOpenSession, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    onOpenSession: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val message = when {
            uiState.loading -> null
            uiState.error != null -> uiState.error
            uiState.items.isEmpty() -> "No finished sessions yet. They'll show up here once you end one."
            else -> null
        }
        when {
            uiState.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            message != null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(message, textAlign = TextAlign.Center) }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    SessionCard(item, uiState.myName, uiState.rivalName, onClick = { onOpenSession(item.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionCard(item: HistoryItem, myName: String, rivalName: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDay(item.startedAt), style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(
                    formatTimes(item.startedAt, item.endedAt).ifEmpty { null },
                    item.venue,
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${item.myWins} – ${item.rivalWins}",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    when {
                        item.myWins > item.rivalWins -> "$myName won"
                        item.rivalWins > item.myWins -> "$rivalName won"
                        else -> "Drawn"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun HistoryContentPreview() {
    val now = Instant.parse("2026-09-21T22:30:00Z")
    RivalsTheme {
        HistoryContent(
            HistoryUiState(
                loading = false,
                myName = "Kevin",
                rivalName = "Dave",
                items = listOf(
                    HistoryItem("1", now.minusSeconds(3 * 3600), now, "The Crown", 3, 1),
                    HistoryItem("2", now.minusSeconds(8 * 86400), now.minusSeconds(8 * 86400 - 7200), null, 2, 2),
                ),
            ),
            {}, {},
        )
    }
}
