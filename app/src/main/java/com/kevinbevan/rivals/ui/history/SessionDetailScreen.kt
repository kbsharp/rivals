package com.kevinbevan.rivals.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.domain.Count
import com.kevinbevan.rivals.domain.Highlight
import com.kevinbevan.rivals.domain.eventTotals
import com.kevinbevan.rivals.domain.highlightsOf
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.components.EmptyState
import com.kevinbevan.rivals.ui.components.ErrorState
import com.kevinbevan.rivals.ui.components.HeadToHead
import com.kevinbevan.rivals.ui.components.IconAction
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.LoadingState
import com.kevinbevan.rivals.ui.components.StatRow
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.TopBar
import com.kevinbevan.rivals.ui.session.ConfirmDialog
import com.kevinbevan.rivals.ui.session.describe
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space
import java.time.Instant

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    /** Saves a finished quick game to a rivalry; `null` when it can't be. */
    onSave: (() -> Unit)? = null,
    viewModel: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }
    SessionDetailContent(uiState, onBack, onDelete = viewModel::delete, onSave = onSave)
}

/**
 * One night, match by match. The scoreboard heads it, each match is a row rather than a card,
 * and its frames are boxed digits in the winner's colour — a tagged frame is ringed in white.
 */
@Composable
internal fun SessionDetailContent(
    uiState: SessionDetailUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: (() -> Unit)? = null,
) {
    val session = uiState.session
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete this session?",
            text = "Its matches and frames go too, and it comes off the head to head. " +
                "This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { confirmingDelete = false; onDelete() },
            onDismiss = { confirmingDelete = false },
        )
    }

    val me = uiState.me
    val rival = uiState.rival
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Rivals.colors.base)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter)
            .padding(bottom = Space.s24),
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        DetailTopBar(
            title = if (session != null) formatDay(session.startedAt) else "Session",
            canDelete = session != null && session.status == Status.ENDED,
            onBack = onBack,
            onDelete = { confirmingDelete = true },
        )

        when {
            uiState.loading -> LoadingState("Opening the session")
            uiState.error != null -> ErrorState(uiState.error)
            me == null || rival == null -> EmptyState(
                title = "Session not found",
                body = "It may have been deleted on the other phone.",
            )
            else -> {
                Header(session, me, rival)
                // A quick game is only on this phone until it's saved: that's the thing to do here.
                if (onSave != null && session?.status == Status.ENDED) PrimaryButton("Save to a rivalry", onSave)
                val totals = remember(uiState.matches, me.uid) { eventTotals(uiState.matches, me.uid) }
                if (totals.isNotEmpty()) NightTotals(totals)
                if (uiState.matches.isEmpty()) {
                    EmptyState(
                        title = "No matches",
                        body = "This session ended before a match was played.",
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s24)) {
                        uiState.matches.forEach { MatchRow(it, me, rival) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    canDelete: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopBar(title, onBack = onBack) {
        if (canDelete) {
            Box {
                IconAction(R.drawable.ic_more_vert, "More", { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Rivals.colors.surface,
                    shape = Shapes.panel,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete session",
                                style = Rivals.type.body,
                                color = Rivals.colors.live,
                            )
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** The night's score, with where and when under it. */
@Composable
private fun Header(session: Session?, me: DetailPlayer, rival: DetailPlayer) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
        Label("Matches won")
        HeadToHead(
            yourScore = me.matchWins,
            rivalScore = rival.matchWins,
            yourName = me.name,
            rivalName = rival.name,
        )
        val detail = listOfNotNull(
            session?.venue?.takeIf { it.isNotBlank() },
            session?.let { formatTimes(it.startedAt, it.endedAt).ifEmpty { null } },
        ).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(detail, style = Rivals.type.body, color = Rivals.colors.fg2)
        }
    }
}

@Composable
private fun MatchRow(item: MatchWithFrames, me: DetailPlayer, rival: DetailPlayer) {
    val match = item.match
    val outcome = when {
        match.status == Status.ACTIVE -> "In progress" to Rivals.colors.fg3
        match.winnerId == me.uid -> "${me.name} won" to Rivals.colors.you
        match.winnerId == rival.uid -> "${rival.name} won" to Rivals.colors.rival
        else -> "No winner" to Rivals.colors.fg3
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(
                "Match ${match.number} · ${match.settings.describe()}",
                modifier = Modifier.weight(1f),
            )
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "${match.frameWins.winsOf(me.uid)} – ${match.frameWins.winsOf(rival.uid)}",
                    style = Rivals.type.number,
                    color = Rivals.colors.fg,
                    textAlign = TextAlign.End,
                )
                Label(outcome.first, color = outcome.second)
            }
        }
        if (item.frames.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s8),
                verticalArrangement = Arrangement.spacedBy(Space.s8),
            ) {
                item.frames.forEach { frame ->
                    val mine = frame.winnerId == me.uid
                    FrameBox(
                        number = frame.number,
                        winnerName = if (mine) me.name else rival.name,
                        events = frame.events,
                        color = if (mine) Rivals.colors.you else Rivals.colors.rival,
                        tint = if (mine) Rivals.colors.youTint else Rivals.colors.rivalTint,
                    )
                }
            }
        }
        val highlights = remember(item) { highlightsOf(item) }
        if (highlights.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                highlights.forEach { HighlightLine(it, me, rival) }
            }
        }
    }
}

/** What happened and when on the left, who did it on the right in their colour. */
@Composable
private fun HighlightLine(highlight: Highlight, me: DetailPlayer, rival: DetailPlayer) {
    val (title, detail) = when (highlight) {
        is Highlight.Shutout -> "Shutout, ${highlight.frames} – 0" to null
        is Highlight.Comeback -> "Came back from ${highlight.trailedBy.first} – ${highlight.trailedBy.second}" to null
        is Highlight.HillHill -> "Won the hill-hill decider" to null
        is Highlight.Run -> "${highlight.frames} frames in a row" to null
        is Highlight.Tagged -> highlight.event.label to "Frame ${highlight.frame}"
        is Highlight.Pack -> "${highlight.size} break & runs in a row" to
            "Frames ${highlight.firstFrame}–${highlight.lastFrame}"
    }
    val mine = highlight.playerId == me.uid
    val name = if (mine) me.name else rival.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s8),
    ) {
        Text(title, style = Rivals.type.body, color = Rivals.colors.fg)
        if (detail != null) {
            Text(detail, style = Rivals.type.caption, color = Rivals.colors.fg3)
        }
        Spacer(Modifier.weight(1f))
        Label(name, color = if (mine) Rivals.colors.you else Rivals.colors.rival, maxLines = 1)
    }
}

/** The night's tagged moments, mirrored like the Stats tab. Absent when nothing was tagged. */
@Composable
private fun NightTotals(totals: Map<FrameEvent, Count>) {
    Column {
        Label("Tonight's highlights", modifier = Modifier.padding(bottom = Space.s4))
        totals.forEach { (event, count) ->
            StatRow(
                event.label,
                "${count.mine}",
                "${count.theirs}",
                yourLead = when {
                    count.mine > count.theirs -> true
                    count.mine < count.theirs -> false
                    else -> null
                },
            )
        }
    }
}

/**
 * One frame: its number, boxed in the winner's colour, ringed in white when something was
 * tagged on it.
 */
@Composable
private fun FrameBox(
    number: Int,
    winnerName: String,
    events: Set<FrameEvent>,
    color: Color,
    tint: Color,
) {
    val tagged = events.isNotEmpty()
    Box(
        modifier = Modifier
            .size(36.dp)
            .then(if (tagged) Modifier.border(2.dp, Rivals.colors.fg, Shapes.chip) else Modifier)
            .padding(if (tagged) 3.dp else 0.dp)
            .background(tint, Shapes.chip)
            .semantics {
                contentDescription = "Frame $number to $winnerName" +
                    events.joinToString("") { ", ${it.label.lowercase()}" }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            color = color,
            style = Rivals.type.number.copy(fontSize = 15.sp),
        )
    }
}

@Preview(heightDp = 900)
@Composable
private fun SessionDetailPreview() {
    val start = Instant.parse("2026-09-21T19:30:00Z")
    fun frames(vararg winners: String) = winners.mapIndexed { i, w -> Frame("f$i", i + 1, w, recordedBy = "a") }
    fun match(n: Int, a: Int, b: Int, winner: String?, raceTo: Int? = 3) = Match(
        "m$n", n, MatchSettings(GameType.NINE_BALL, raceTo), Status.ENDED, mapOf("a" to a, "b" to b), winner,
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
                rival = DetailPlayer("b", "Julian", 1),
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
