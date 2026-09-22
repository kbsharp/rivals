package com.kevinbevan.rivals.ui.rivalry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevinbevan.rivals.ui.components.EmptyState
import com.kevinbevan.rivals.ui.components.ErrorState
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.history.formatDay
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.Space
import java.time.Duration
import java.time.Instant

/**
 * The nights you've played, newest first: the date, where and how long, the score, and who took
 * it. Rows, not cards — space separates them, and only the "you won" caption carries colour.
 */
@Composable
internal fun SessionsTab(uiState: RivalryUiState, onOpen: (String) -> Unit) {
    when {
        uiState.error != null -> ErrorState(uiState.error)
        uiState.sessions.isEmpty() -> EmptyState(
            title = "No nights yet",
            body = "Start a session and it'll be here once you end it, with every match and " +
                "frame you played.",
        )
        else -> Column {
            uiState.sessions.forEach { item ->
                SessionRow(item, uiState.rivalName, onClick = { onOpen(item.id) })
            }
        }
    }
}

@Composable
private fun SessionRow(item: SessionItem, rivalName: String, onClick: () -> Unit) {
    val outcome = when {
        item.myWins > item.rivalWins -> "You won" to Rivals.colors.you
        item.rivalWins > item.myWins -> "$rivalName won" to Rivals.colors.rival
        else -> "Drawn" to Rivals.colors.fg3
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = Space.s8)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(formatDay(item.startedAt))
            val detail = listOfNotNull(
                item.venue?.takeIf { it.isNotBlank() },
                formatLength(item.startedAt, item.endedAt),
            ).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = Rivals.type.body, color = Rivals.colors.fg2)
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "${item.myWins} – ${item.rivalWins}",
                style = Rivals.type.number,
                color = Rivals.colors.fg,
                textAlign = TextAlign.End,
            )
            Label(outcome.first, color = outcome.second)
        }
    }
}

/** How long the night ran: "3h", "2h 30m", "45m". Empty when it isn't known. */
internal fun formatLength(start: Instant?, end: Instant?): String {
    if (start == null || end == null) return ""
    val minutes = Duration.between(start, end).toMinutes()
    if (minutes <= 0) return ""
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "${rest}m"
        rest == 0L -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}
