package com.kevinbevan.rivals.ui.rivalry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kevinbevan.rivals.domain.Record
import com.kevinbevan.rivals.ui.components.EmptyState
import com.kevinbevan.rivals.ui.components.ErrorState
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.StatRow
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.Space

/**
 * The mirrored table: your value, what it is, theirs. The leader's number is white and heavy and
 * the other `fg-3`, so the whole table can be read straight down the middle.
 */
@Composable
internal fun StatsTab(uiState: RivalryUiState) {
    val stats = uiState.stats
    when {
        uiState.error != null -> ErrorState(uiState.error)
        stats == null -> EmptyState(
            title = "No stats yet",
            body = "They build up from your first night together.",
        )
        stats.frames.played == 0 -> EmptyState(
            title = "No frames played yet",
            body = "Play a match and the numbers start here.",
        )
        else -> Column {
            StatRow("Matches", "${stats.matches.won}", "${stats.matches.lost}", yourLead = lead(stats.matches))
            StatRow("Frames", "${stats.frames.won}", "${stats.frames.lost}", yourLead = lead(stats.frames))
            StatRow(
                "Nights won",
                "${stats.nights.won}",
                "${stats.nights.lost}",
                yourLead = compare(stats.nights.won, stats.nights.lost),
            )
            if (stats.nights.drawn > 0) {
                // Under the label it belongs to, not under either player's number.
                Text(
                    "${stats.nights.drawn} drawn",
                    style = Rivals.type.caption,
                    color = Rivals.colors.fg3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.s8),
                )
            }
            val myRun = stats.longestStreaks[stats.myId] ?: 0
            val theirRun = stats.longestStreaks[stats.rivalId] ?: 0
            StatRow("Longest run", "$myRun", "$theirRun", yourLead = compare(myRun, theirRun))

            stats.specials.forEach { (event, count) ->
                StatRow(
                    event.label,
                    "${count.mine}",
                    "${count.theirs}",
                    yourLead = compare(count.mine, count.theirs),
                )
            }

            if (stats.byGameType.isNotEmpty()) {
                stats.byGameType.forEach { (type, game) ->
                    Column(
                        Modifier.padding(top = Space.s16),
                        verticalArrangement = Arrangement.spacedBy(Space.s4),
                    ) {
                        Label(type.label)
                        StatRow(
                            "Matches",
                            "${game.matches.won}",
                            "${game.matches.lost}",
                            yourLead = lead(game.matches),
                        )
                        StatRow(
                            "Frames",
                            "${game.frames.won}",
                            "${game.frames.lost}",
                            yourLead = lead(game.frames),
                        )
                    }
                }
            }
        }
    }
}

private fun lead(record: Record): Boolean? = compare(record.won, record.lost)

/** `true` you lead, `false` they do, `null` level: neither number is picked out. */
private fun compare(mine: Int, theirs: Int): Boolean? = when {
    mine > theirs -> true
    theirs > mine -> false
    else -> null
}
