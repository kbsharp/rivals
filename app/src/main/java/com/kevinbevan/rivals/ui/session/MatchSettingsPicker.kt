package com.kevinbevan.rivals.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.theme.RivalsTheme

val DefaultMatchSettings = MatchSettings(GameType.EIGHT_BALL, raceTo = 5)

private const val MAX_RACE = 21

/** Game type and race-to. Remembers the last race length while "open-ended" is on. */
@Composable
fun MatchSettingsPicker(
    settings: MatchSettings,
    onChange: (MatchSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastRace by remember { mutableIntStateOf(settings.raceTo ?: DefaultMatchSettings.raceTo!!) }
    val race = settings.raceTo

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GameType.entries.forEachIndexed { i, type ->
                SegmentedButton(
                    selected = settings.gameType == type,
                    onClick = { onChange(settings.copy(gameType = type)) },
                    shape = SegmentedButtonDefaults.itemShape(i, GameType.entries.size),
                ) { Text(type.label) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Race to", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            FilledTonalIconButton(
                onClick = { onChange(settings.copy(raceTo = race!! - 1)) },
                enabled = race != null && race > 1,
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                text = race?.toString() ?: "–",
                modifier = Modifier.widthIn(min = 48.dp),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            FilledTonalIconButton(
                onClick = { onChange(settings.copy(raceTo = race!! + 1)) },
                enabled = race != null && race < MAX_RACE,
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Open-ended", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No race; end the match by hand",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = race == null,
                onCheckedChange = { open ->
                    if (open) lastRace = race ?: lastRace
                    onChange(settings.copy(raceTo = if (open) null else lastRace))
                },
            )
        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { MatchSettingsPicker(settings, { settings = it }) },
        confirmButton = { TextButton(onClick = { onConfirm(settings) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun MatchSettings.describe(): String =
    gameType.label + " · " + (raceTo?.let { "race to $it" } ?: "open-ended")

@Preview(showBackground = true)
@Composable
private fun MatchSettingsPickerPreview() {
    RivalsTheme { MatchSettingsPicker(DefaultMatchSettings, {}) }
}
