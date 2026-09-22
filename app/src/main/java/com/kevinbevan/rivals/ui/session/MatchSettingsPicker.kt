package com.kevinbevan.rivals.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space
import androidx.compose.ui.tooling.preview.Preview

val DefaultMatchSettings = MatchSettings(GameType.EIGHT_BALL, raceTo = 5)

private const val MAX_RACE = 21

/**
 * Game type and race-to, in the brief's chips rather than Material's segmented buttons.
 * Remembers the last race length while "open-ended" is on.
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
            Label("Game")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                GameType.entries.forEach { type ->
                    ChoiceChip(
                        text = type.label,
                        selected = settings.gameType == type,
                        onClick = { onChange(settings.copy(gameType = type)) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Label("Race to")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s12),
            ) {
                Stepper("−", "One fewer frame", enabled = race != null && race > 1) {
                    onChange(settings.copy(raceTo = race!! - 1))
                }
                Text(
                    text = race?.toString() ?: "–",
                    modifier = Modifier.widthIn(min = 48.dp),
                    style = Rivals.type.headline.copy(fontSize = 28.sp),
                    color = if (race == null) Rivals.colors.fg3 else Rivals.colors.fg,
                    textAlign = TextAlign.Center,
                )
                Stepper("+", "One more frame", enabled = race != null && race < MAX_RACE) {
                    onChange(settings.copy(raceTo = race!! + 1))
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
                Text(
                    "No race; end the match by hand.",
                    style = Rivals.type.caption,
                    color = Rivals.colors.fg3,
                )
            }
        }
    }
}

/** A chip that is either on (white fill, charcoal text) or off (`raised`, `fg-2`). */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) Rivals.colors.fg else Rivals.colors.raised,
                Shapes.pill,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.s16, vertical = Space.s12),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, color = if (selected) Rivals.colors.onFg else Rivals.colors.fg2)
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
    var settings by remember { mutableStateOf(initial) }
    RivalsDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(settings) },
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
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Rivals.colors.surface,
        shape = Shapes.panel,
        title = { Text(title, style = Rivals.type.headline, color = Rivals.colors.fg) },
        text = { content() },
        confirmButton = { SecondaryButton(confirmLabel, onConfirm, destructive = destructive) },
        dismissButton = { TextAction("Cancel", onDismiss) },
    )
}

fun MatchSettings.describe(): String =
    gameType.label + " · " + (raceTo?.let { "race to $it" } ?: "open-ended")

@Preview(showBackground = true)
@Composable
private fun MatchSettingsPickerPreview() {
    RivalsTheme {
        Box(Modifier.background(Rivals.colors.base).padding(Space.s16)) {
            MatchSettingsPicker(DefaultMatchSettings, {})
        }
    }
}
