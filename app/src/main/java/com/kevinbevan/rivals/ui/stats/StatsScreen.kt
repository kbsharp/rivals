package com.kevinbevan.rivals.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.domain.Count
import com.kevinbevan.rivals.domain.GameTypeStats
import com.kevinbevan.rivals.domain.NightsRecord
import com.kevinbevan.rivals.domain.Record
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.Streak
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(uiState, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsContent(uiState: StatsUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val stats = uiState.stats
        val message = when {
            uiState.loading -> null
            uiState.error != null -> uiState.error
            stats == null -> "No sessions yet. Your stats build up from your first night together."
            stats.frames.played == 0 -> "No frames played yet. Your stats will build up from your first night."
            else -> null
        }
        when {
            uiState.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            message != null || stats == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(message.orEmpty(), textAlign = TextAlign.Center) }
            else -> StatsList(stats, uiState.myName, uiState.rivalName, Modifier.padding(padding))
        }
    }
}

@Composable
private fun StatsList(stats: Stats, me: String, rival: String, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Legend(me, rival) }
        item {
            Section("Matches") { RecordRow(stats.matches, me, rival) }
        }
        item {
            Section("Frames") { RecordRow(stats.frames, me, rival) }
        }
        item {
            Section("Nights") { NightsRow(stats.nights, me) }
        }
        item {
            Section("Streaks") { Streaks(stats, me, rival) }
        }
        if (stats.byGameType.isNotEmpty()) {
            item {
                Section("By game") { ByGame(stats.byGameType, me, rival) }
            }
        }
        item {
            Section("Specials") { Specials(stats.specials, me, rival) }
        }
    }
}

/** Which colour is whom. Every bar is also labelled with names and numbers. */
@Composable
private fun Legend(me: String, rival: String) {
    val colors = Rivals.colors
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendKey(me, colors.you)
        LegendKey(rival, colors.rival)
    }
}

@Composable
private fun LegendKey(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** "Kevin 12 – 9 Julian", a split bar, and each side's win rate. */
@Composable
private fun RecordRow(record: Record, me: String, rival: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$me ${record.won}", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        Text(
            "${record.lost} $rival",
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.End,
        )
    }
    SplitBar(record.won, record.lost, "$me ${record.won}, $rival ${record.lost}")
    Row {
        Text(percent(record.winRate), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            percent(record.winRate?.let { 1 - it }),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * One bar split in proportion between the players, with a 2dp gap between the two parts and
 * rounded outer ends. An even grey bar when nothing has been played.
 */
@Composable
private fun SplitBar(mine: Int, theirs: Int, description: String) {
    val colors = Rivals.colors
    val shape = RoundedCornerShape(4.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (mine + theirs == 0) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape))
            return@Row
        }
        if (mine > 0) {
            Box(Modifier.weight(mine.toFloat()).height(12.dp).background(colors.you, shape))
        }
        if (theirs > 0) {
            Box(Modifier.weight(theirs.toFloat()).height(12.dp).background(colors.rival, shape))
        }
    }
}

@Composable
private fun NightsRow(nights: NightsRecord, me: String) {
    Text(
        "$me won ${nights.won}, lost ${nights.lost}" + if (nights.drawn > 0) ", drew ${nights.drawn}" else "",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun Streaks(stats: Stats, me: String, rival: String) {
    fun nameOf(id: String?) = if (id == stats.myId) me else rival
    val current: Streak = stats.currentStreak
    Text(
        when {
            current.playerId == null -> "No matches finished yet"
            current.length == 1 -> "${nameOf(current.playerId)} won the last match"
            else -> "${nameOf(current.playerId)} has won the last ${current.length} matches"
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "Longest run: $me ${stats.longestStreaks[stats.myId] ?: 0}, $rival ${stats.longestStreaks[stats.rivalId] ?: 0}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ByGame(byGameType: Map<GameType, GameTypeStats>, me: String, rival: String) {
    byGameType.entries.forEachIndexed { i, (type, s) ->
        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(type.label, style = MaterialTheme.typography.titleSmall)
        Row {
            Text("Matches", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("${s.matches.won} – ${s.matches.lost}", style = MaterialTheme.typography.bodyMedium)
        }
        SplitBar(s.matches.won, s.matches.lost, "${type.label} matches: $me ${s.matches.won}, $rival ${s.matches.lost}")
        Text(
            "Frames ${s.frames.won} – ${s.frames.lost}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Specials(specials: Map<FrameEvent, Count>, me: String, rival: String) {
    if (specials.values.all { it.mine + it.theirs == 0 }) {
        Text(
            "Nothing tagged yet. Tag a frame from the game menu during a match.",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Row {
        Spacer(Modifier.weight(1f))
        Text(me, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, maxLines = 1)
        Text(rival, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, maxLines = 1)
    }
    specials.forEach { (event, count) ->
        Row(Modifier.semantics(mergeDescendants = true) {}) {
            Text(event.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${count.mine}", Modifier.width(72.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
            Text("${count.theirs}", Modifier.width(72.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
        }
    }
}

private fun percent(rate: Double?): String = rate?.let { "${(it * 100).roundToInt()}%" } ?: "–"

@Preview(heightDp = 1400)
@Composable
private fun StatsPreview() {
    RivalsTheme {
        StatsContent(
            StatsUiState(
                loading = false,
                myName = "Kevin",
                rivalName = "Julian",
                stats = Stats(
                    myId = "a",
                    rivalId = "b",
                    matches = Record(12, 9),
                    frames = Record(61, 55),
                    nights = NightsRecord(4, 2, 1),
                    byGameType = mapOf(
                        GameType.EIGHT_BALL to GameTypeStats(Record(9, 5), Record(44, 35)),
                        GameType.NINE_BALL to GameTypeStats(Record(3, 4), Record(17, 20)),
                    ),
                    currentStreak = Streak("b", 2),
                    longestStreaks = mapOf("a" to 5, "b" to 3),
                    specials = mapOf(FrameEvent.BREAK_AND_RUN to Count(3, 1), FrameEvent.GOLDEN_BREAK to Count(0, 1)),
                ),
            ),
            onBack = {},
        )
    }
}
