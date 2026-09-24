package com.kevinbevan.rivals.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.components.ChoiceRow
import com.kevinbevan.rivals.ui.components.RivalsTextField
import com.kevinbevan.rivals.ui.rememberMatchDefaults
import com.kevinbevan.rivals.ui.theme.Space

/** Starting a night against a rival, from Home or the head to head: match settings for the first match, plus an optional venue. */
@Composable
internal fun NewSessionDialog(
    recentVenues: List<String>,
    onStart: (MatchSettings, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaults = rememberMatchDefaults()
    var settings by remember { mutableStateOf(defaults.last) }
    var venue by rememberSaveable { mutableStateOf("") }
    RivalsDialog(
        title = "New session",
        confirmLabel = "Start",
        onConfirm = {
            defaults.last = settings
            onStart(settings, venue.trim().ifEmpty { null })
        },
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s16),
        ) {
            MatchSettingsPicker(settings, { settings = it })
            RivalsTextField(
                value = venue,
                onValueChange = { venue = it.take(40) },
                label = "Venue",
                placeholder = "Optional",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            if (recentVenues.isNotEmpty()) {
                ChoiceRow(
                    label = "Recent",
                    options = recentVenues.map { it to it },
                    selected = recentVenues.firstOrNull { venue.trim().equals(it, ignoreCase = true) },
                    onSelect = { venue = it },
                )
            }
        }
    }
}
