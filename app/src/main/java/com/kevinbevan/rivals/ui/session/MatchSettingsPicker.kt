package com.kevinbevan.rivals.ui.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.SecondaryButton
import com.kevinbevan.rivals.ui.components.TextAction
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
 * Game and race-to, in the brief's chips rather than Material's segmented buttons.
 *
 * A game is optional: nothing is picked until you pick one, and tapping the chosen game again
 * clears it, leaving a match that only tracks a score. The race remembers its last length while
 * "open-ended" is on.
 */
@Composable
fun MatchSettingsPicker(
    settings: MatchSettings,
    onChange: (MatchSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastRace by remember { mutableIntStateOf(settings.raceTo ?: DefaultMatchSettings.raceTo!!) }
    val race = settings.raceTo

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s16)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            // The label carries "optional" rather than a line under the chips, which would
            // appear and disappear as a game is picked and push the race row about.
            Label(if (settings.gameType == null) "Game · optional" else "Game")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s8),
                verticalArrangement = Arrangement.spacedBy(Space.s8),
            ) {
                GameType.entries.forEach { type ->
                    val chosen = settings.gameType == type
                    ChoiceChip(
                        text = type.label,
                        selected = chosen,
                        onClick = { onChange(settings.copy(gameType = if (chosen) null else type)) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Label("Race to")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s12),
                verticalArrangement = Arrangement.spacedBy(Space.s8),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s12),
                ) {
                    Stepper("−", "One fewer frame", enabled = race != null && race > 1) {
                        onChange(settings.copy(raceTo = race!! - 1))
                    }
                    Text(
                        text = race?.toString() ?: "–",
                        modifier = Modifier.widthIn(min = 44.dp),
                        style = Rivals.type.headline.copy(fontSize = 28.sp),
                        color = if (race == null) Rivals.colors.fg3 else Rivals.colors.fg,
                        textAlign = TextAlign.Center,
                    )
                    Stepper("+", "One more frame", enabled = race != null && race < MAX_RACE) {
                        onChange(settings.copy(raceTo = race!! + 1))
                    }
                }
                ChoiceChip(
                    text = "Open-ended",
                    selected = race == null,
                    onClick = {
                        val open = race != null
                        if (open) lastRace = race
                        onChange(settings.copy(raceTo = if (open) null else lastRace))
                    },
                )
            }
            if (race == null) {
                Hint("No race; end the match by hand.")
            }
        }
    }
}

/** The quiet line under a row of chips that says what the choice means. */
@Composable
private fun Hint(text: String) {
    Text(text, style = Rivals.type.caption, color = Rivals.colors.fg3)
}

/**
 * A chip that is either on or off. On is the app's accent — [RivalsColors.you] on its tint,
 * the same teal that names you everywhere else — so a row of chips reads at a glance instead
 * of as black and white.
 */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) Rivals.colors.youTint else Rivals.colors.raised,
        Motion.tween(Motion.FAST),
        label = "chip background",
    )
    val content by animateColorAsState(
        if (selected) Rivals.colors.you else Rivals.colors.fg2,
        Motion.tween(Motion.FAST),
        label = "chip text",
    )
    Box(
        modifier = Modifier
            .heightIn(min = Space.touch)
            .background(background, Shapes.pill)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s16, vertical = Space.s12),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, color = content, maxLines = 1)
    }
}

@Composable
private fun Stepper(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Space.touch)
            .alpha(if (enabled) 1f else 0.4f)
            .background(Rivals.colors.raised, CircleShape)
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
    val race = raceTo?.let { "race to $it" } ?: "open-ended"
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
