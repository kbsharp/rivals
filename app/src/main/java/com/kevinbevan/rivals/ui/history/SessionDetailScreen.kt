package com.kevinbevan.rivals.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.session.describe
import com.kevinbevan.rivals.ui.theme.LocalPlayerColors
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.time.Instant

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }
    SessionDetailContent(uiState, onBack, onDelete = viewModel::delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDetailContent(uiState: SessionDetailUiState, onBack: () -> Unit, onDelete: () -> Unit) {
    val session = uiState.session
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("Its matches and frames go too, and it comes off the head-to-head. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (session != null) formatDay(session.startedAt) else "Session")
                        if (session != null) {
                            val details = listOfNotNull(
                                formatTimes(session.startedAt, session.endedAt).ifEmpty { null },
                                session.venue,
                            ).joinToString(" · ")
                            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null && session.status == Status.ENDED) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete session") },
                                onClick = {
                                    menuOpen = false
                                    confirmingDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val me = uiState.me
        val rival = uiState.rival
        when {
            uiState.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.error != null || me == null || rival == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(uiState.error.orEmpty(), textAlign = TextAlign.Center) }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Summary(me, rival) }
                if (uiState.matches.isEmpty()) {
                    item { Text("No matches were played.", Modifier.padding(8.dp)) }
                }
                items(uiState.matches, key = { it.match.id }) { MatchCard(it, me, rival) }
            }
        }
    }
}

@Composable
private fun Summary(me: DetailPlayer, rival: DetailPlayer) {
    val colors = LocalPlayerColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NameTag(me.name, colors.me, colors.onMe)
            Text(
                "${me.matchWins} – ${rival.matchWins}",
                style = MaterialTheme.typography.displaySmall,
            )
            NameTag(rival.name, colors.rival, colors.onRival)
        }
        Text(
            "matches won",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NameTag(name: String, container: Color, content: Color) {
    Text(
        name,
        modifier = Modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = content,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
    )
}

@Composable
private fun MatchCard(item: MatchWithFrames, me: DetailPlayer, rival: DetailPlayer) {
    val match = item.match
    val colors = LocalPlayerColors.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Match ${match.number}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        match.settings.describe(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${match.frameWins.winsOf(me.uid)} – ${match.frameWins.winsOf(rival.uid)}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        when {
                            match.status == Status.ACTIVE -> "In progress"
                            match.winnerId == me.uid -> "${me.name} won"
                            match.winnerId == rival.uid -> "${rival.name} won"
                            else -> "No winner"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.frames.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.frames.forEach { frame ->
                        val mine = frame.winnerId == me.uid
                        FrameDot(
                            number = frame.number,
                            winnerName = if (mine) me.name else rival.name,
                            events = frame.events,
                            container = if (mine) colors.me else colors.rival,
                            content = if (mine) colors.onMe else colors.onRival,
                        )
                    }
                }
                val tagged = item.frames.filter { it.events.isNotEmpty() }
                if (tagged.isNotEmpty()) {
                    Text(
                        tagged.joinToString(" · ") { f ->
                            val who = if (f.winnerId == me.uid) me.name else rival.name
                            f.events.joinToString(", ") { it.label } + ": frame ${f.number}, $who"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One frame: its number, in the winner's colour, ringed when something was tagged on it. */
@Composable
private fun FrameDot(
    number: Int,
    winnerName: String,
    events: Set<FrameEvent>,
    container: Color,
    content: Color,
) {
    val tagged = events.isNotEmpty()
    Box(
        modifier = Modifier
            .size(32.dp)
            .then(if (tagged) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .padding(if (tagged) 3.dp else 0.dp)
            .background(container, CircleShape)
            .semantics {
                contentDescription = "Frame $number to $winnerName" +
                    events.joinToString("") { ", ${it.label.lowercase()}" }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(heightDp = 800)
@Composable
private fun SessionDetailPreview() {
    val start = Instant.parse("2026-09-21T19:30:00Z")
    fun frames(vararg winners: String) = winners.mapIndexed { i, w -> Frame("f$i", i + 1, w, recordedBy = "a") }
    fun match(n: Int, a: Int, b: Int, winner: String?, raceTo: Int? = 3) = Match(
        "m$n", n, MatchSettings(GameType.EIGHT_BALL, raceTo), Status.ENDED, mapOf("a" to a, "b" to b), winner,
    )
    RivalsTheme {
        SessionDetailContent(
            SessionDetailUiState(
                loading = false,
                session = Session(
                    "s", listOf("a", "b"), Status.ENDED, start, start.plusSeconds(10_800),
                    venue = "The Crown", createdBy = "a", matchWins = mapOf("a" to 1, "b" to 1),
                ),
                me = DetailPlayer("a", "Kevin", 1),
                rival = DetailPlayer("b", "Dave", 1),
                matches = listOf(
                    MatchWithFrames(match(1, 3, 1, "a"), frames("a", "b", "a", "a")),
                    MatchWithFrames(match(2, 2, 3, "b"), frames("b", "a", "b", "a", "b")),
                    MatchWithFrames(match(3, 1, 1, null, raceTo = null), frames("a", "b")),
                ),
            ),
            onBack = {},
            onDelete = {},
        )
    }
}
