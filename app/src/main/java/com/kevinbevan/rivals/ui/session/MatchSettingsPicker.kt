package com.kevinbevan.rivals.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.SecondaryButton
import com.kevinbevan.rivals.ui.components.TextAction
import com.kevinbevan.rivals.ui.components.ToggleChip
import androidx.compose.ui.platform.testTag
import com.kevinbevan.rivals.ui.rememberMatchDefaults
import com.kevinbevan.rivals.ui.theme.Motion
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space
import androidx.compose.ui.tooling.preview.Preview

/** What a picker opens on when nothing has been played on this phone yet. */
val DefaultMatchSettings = MatchSettings(gameType = null, raceTo = 5)

private const val MAX_RACE = 21

/**
 * Game and race-to as a two-by-two grid: the two games side by side, and under them the race
 * stepper beside "No limit", every cell the same width. The one picker everywhere a match is
 * set up or changed: starting a night, between matches, the match sheet and the match-won panel.
 *
 * A game is optional: nothing is picked until you pick one, and tapping the chosen game again
 * clears it, leaving a match that only tracks a score. The race remembers its last length while
 * "No limit" is on, and shows it dimmed. It can't go below [minRace], the leader's score plus one.
 *
 * [gameAction] and [raceAction] end the game and race rows, so an action beside the picker (the
 * match sheet's End match and End session) lines up with the chips rather than floating.
 */
@Composable
fun MatchSettingsPicker(
    settings: MatchSettings,
    onChange: (MatchSettings) -> Unit,
    modifier: Modifier = Modifier,
    minRace: Int = 1,
    gameLabel: String = "Game",
    gameAction: (@Composable RowScope.() -> Unit)? = null,
    raceAction: (@Composable RowScope.() -> Unit)? = null,
) {
    var lastRace by remember { mutableIntStateOf(settings.raceTo ?: DefaultMatchSettings.raceTo!!) }
    val race = settings.raceTo

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s16)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            // The label carries "optional" rather than a line under the chips, which would
            // appear and disappear as a game is picked and push the race row about.
            Label(if (settings.gameType == null) "$gameLabel · optional" else gameLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                GameType.entries.forEach { type ->
                    val chosen = settings.gameType == type
                    ChoiceChip(
                        text = type.label,
                        selected = chosen,
                        onClick = { onChange(settings.copy(gameType = if (chosen) null else type)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                gameAction?.invoke(this)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Label("Race to")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(Space.touch)
                        .alpha(if (race == null) 0.45f else 1f)
                        .background(Rivals.colors.raised, Shapes.pill),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stepper("−", "One fewer frame", enabled = race != null && race > minRace) {
                        onChange(settings.copy(raceTo = race!! - 1))
                    }
                    Text(
                        text = (race ?: lastRace).toString(),
                        modifier = Modifier.testTag("race-to"),
                        style = Rivals.type.number,
                        color = Rivals.colors.fg,
                        textAlign = TextAlign.Center,
                    )
                    Stepper("+", "One more frame", enabled = race != null && race < MAX_RACE) {
                        onChange(settings.copy(raceTo = race!! + 1))
                    }
                }
                ChoiceChip(
                    text = "No limit",
                    selected = race == null,
                    onClick = {
                        val open = race != null
                        if (open) lastRace = race
                        onChange(settings.copy(raceTo = if (open) null else lastRace.coerceAtLeast(minRace)))
                    },
                    modifier = Modifier.weight(1f),
                )
                raceAction?.invoke(this)
            }
            if (race == null) {
                Hint("You'll end this one from the menu.")
            }
        }
    }
}

/** The quiet line under a row of chips that says what the choice means. */
@Composable
private fun Hint(text: String) {
    Text(text, style = Rivals.type.caption, color = Rivals.colors.fg3)
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ToggleChip(text, selected, onClick, modifier, role = Role.RadioButton)

/** A − or + inside the race cell: a full touch target, drawn as just its glyph. */
@Composable
private fun Stepper(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Space.touch)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Rivals.type.title, color = Rivals.colors.fg)
    }
}

/** A dialog around [MatchSettingsPicker], for starting or changing a match. */
@Composable
fun MatchSettingsDialog(
    title: String,
    confirmLabel: String,
    initial: MatchSettings,
    onConfirm: (MatchSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaults = rememberMatchDefaults()
    var settings by remember { mutableStateOf(initial) }
    RivalsDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = {
            defaults.last = settings
            onConfirm(settings)
        },
        onDismiss = onDismiss,
    ) {
        MatchSettingsPicker(settings, { settings = it })
    }
}

/** Confirming something that can't simply be undone: say what it will mean, then ask. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    RivalsDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = destructive,
    ) {
        if (text.isNotBlank()) {
            Text(text, style = Rivals.type.body, color = Rivals.colors.fg2)
        }
    }
}

/** The app's one dialog shape: a `surface` panel, a Montserrat title, one confirming action. */
@Composable
fun RivalsDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rivals.colors.surface,
        shape = Shapes.panel,
        title = { Text(title, style = Rivals.type.headline, color = Rivals.colors.fg) },
        text = { content() },
        confirmButton = {
            SecondaryButton(
                confirmLabel,
                onConfirm,
                enabled = confirmEnabled,
                destructive = destructive,
            )
        },
        dismissButton = { TextAction("Cancel", onDismiss) },
    )
}

fun MatchSettings.describe(): String {
    val race = raceTo?.let { "race to $it" } ?: "no limit"
    return gameType?.let { "${it.label} · $race" } ?: race
}

@Preview(showBackground = true)
@Composable
private fun MatchSettingsPickerPreview() {
    RivalsTheme {
        Box(Modifier.background(Rivals.colors.base).padding(Space.s16)) {
            MatchSettingsPicker(DefaultMatchSettings, {})
        }
    }
}
