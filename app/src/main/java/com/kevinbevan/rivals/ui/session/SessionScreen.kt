package com.kevinbevan.rivals.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.theme.LocalPlayerColors
import com.kevinbevan.rivals.ui.theme.RivalsTheme

@Composable
fun SessionScreen(
    onExit: () -> Unit,
    viewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.ended) {
        if (uiState.ended) onExit()
    }

    // The phone sits on the table between shots; don't let it lock mid-frame.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    SessionContent(
        uiState = uiState,
        actions = SessionActions(
            onBack = onExit,
            onRecordFrame = viewModel::recordFrame,
            onChooseBreaker = viewModel::chooseBreaker,
            onUndo = viewModel::undo,
            onEndMatch = viewModel::endMatch,
            onStartMatch = viewModel::startMatch,
            onChangeSettings = viewModel::changeSettings,
            onEndSession = viewModel::endSession,
            onMessageShown = viewModel::dismissMessage,
        ),
    )
}

class SessionActions(
    val onBack: () -> Unit = {},
    val onRecordFrame: (String) -> Unit = {},
    val onChooseBreaker: (String) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onEndMatch: () -> Unit = {},
    val onStartMatch: (MatchSettings) -> Unit = {},
    val onChangeSettings: (MatchSettings) -> Unit = {},
    val onEndSession: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

private enum class SessionDialog { CHANGE_SETTINGS, END_MATCH, END_SESSION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionContent(uiState: SessionUiState, actions: SessionActions) {
    var dialog by rememberSaveable { mutableStateOf<SessionDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val match = uiState.match

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (match != null) "Match ${match.number}" else "Between matches")
                        if (match != null) {
                            Text(match.settings.describe(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.pendingSync) {
                        Icon(
                            painterResource(R.drawable.ic_cloud_upload),
                            contentDescription = "Saved on this phone, waiting to sync",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (match != null && match.framesPlayed == 0) {
                                DropdownMenuItem(
                                    text = { Text("Change game") },
                                    onClick = { menuOpen = false; dialog = SessionDialog.CHANGE_SETTINGS },
                                )
                            }
                            if (match != null && match.framesPlayed > 0) {
                                DropdownMenuItem(
                                    text = { Text("End match") },
                                    onClick = { menuOpen = false; dialog = SessionDialog.END_MATCH },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("End session") },
                                onClick = { menuOpen = false; dialog = SessionDialog.END_SESSION },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val me = uiState.me
        val rival = uiState.rival
        if (uiState.loading || me == null || rival == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Top half: glanceable state and undo.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Tonight", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${me.name} ${me.matches} – ${rival.matches} ${rival.name}",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (uiState.frameWinners.isNotEmpty()) {
                    Text(
                        uiState.frameWinners.joinToString("  ") { it.take(1) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = actions.onUndo,
                    enabled = uiState.canUndo,
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_undo), contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Undo last frame")
                }
            }

            // Bottom half, within thumb reach: the score buttons, or the next-match setup.
            if (match != null) {
                BreakerSelector(
                    me = me,
                    rival = rival,
                    breakerId = uiState.breakerId,
                    onChoose = actions.onChooseBreaker,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Box(Modifier.weight(1.3f).fillMaxWidth()) {
                if (match != null) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScoreButton(
                            name = me.name,
                            frames = me.frames,
                            onTheHill = match.settings.raceTo?.let { me.frames == it - 1 } == true,
                            container = LocalPlayerColors.current.me,
                            content = LocalPlayerColors.current.onMe,
                            onClick = { actions.onRecordFrame(me.uid) },
                            modifier = Modifier.weight(1f).testTag("score-${me.uid}"),
                        )
                        ScoreButton(
                            name = rival.name,
                            frames = rival.frames,
                            onTheHill = match.settings.raceTo?.let { rival.frames == it - 1 } == true,
                            container = LocalPlayerColors.current.rival,
                            content = LocalPlayerColors.current.onRival,
                            onClick = { actions.onRecordFrame(rival.uid) },
                            modifier = Modifier.weight(1f).testTag("score-${rival.uid}"),
                        )
                    }
                } else {
                    NextMatchPanel(
                        initial = uiState.lastSettings,
                        onStart = actions.onStartMatch,
                        onEndSession = { dialog = SessionDialog.END_SESSION },
                    )
                }
            }
        }
    }

    when (dialog) {
        SessionDialog.CHANGE_SETTINGS -> MatchSettingsDialog(
            title = "Change game",
            confirmLabel = "Save",
            initial = match?.settings ?: uiState.lastSettings,
            onConfirm = { dialog = null; actions.onChangeSettings(it) },
            onDismiss = { dialog = null },
        )
        SessionDialog.END_MATCH -> ConfirmDialog(
            title = "End this match?",
            text = match?.let { endMatchConsequence(it, uiState) }.orEmpty(),
            confirmLabel = "End match",
            onConfirm = { dialog = null; actions.onEndMatch() },
            onDismiss = { dialog = null },
        )
        SessionDialog.END_SESSION -> ConfirmDialog(
            title = "End tonight's session?",
            text = buildString {
                val me = uiState.me
                val rival = uiState.rival
                val nothingPlayed = me != null && rival != null && me.matches + rival.matches == 0 &&
                    !uiState.canUndo && (match?.framesPlayed ?: 0) == 0
                if (nothingPlayed) {
                    append("Nothing's been played, so this session will be deleted rather than kept in History.")
                } else if (me != null && rival != null) {
                    append("Final score: ${me.name} ${me.matches} – ${rival.matches} ${rival.name}.")
                }
                if (match != null && match.framesPlayed > 0) {
                    append("\n\nThe match in progress will be ended too. ")
                    append(endMatchConsequence(match, uiState))
                }
            },
            confirmLabel = "End session",
            onConfirm = { dialog = null; actions.onEndSession() },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** What ending [match] by hand will mean, per [ScoreRules.manualWinner]. */
private fun endMatchConsequence(match: Match, uiState: SessionUiState): String {
    if (match.settings.raceTo != null) {
        return "Nobody has reached ${match.settings.raceTo} yet, so it won't count for either of you."
    }
    val winner = ScoreRules.manualWinner(match)
        ?: return "It's level, so it won't count for either of you."
    val name = listOfNotNull(uiState.me, uiState.rival).firstOrNull { it.uid == winner }?.name ?: "The leader"
    return "$name is ahead, so it goes down as their win."
}

@Composable
private fun ScoreButton(
    name: String,
    frames: Int,
    onTheHill: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(frames.toString(), fontSize = 112.sp, lineHeight = 112.sp)
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (onTheHill) "on the hill" else " ",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Who's breaking the next frame. Alternates by itself; tap to correct it. */
@Composable
private fun BreakerSelector(
    me: PlayerSide,
    rival: PlayerSide,
    breakerId: String?,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Breaking", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            listOf(me, rival).forEachIndexed { i, player ->
                SegmentedButton(
                    selected = breakerId == player.uid,
                    onClick = { onChoose(player.uid) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    modifier = Modifier.testTag("breaker-${player.uid}"),
                ) { Text(player.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun NextMatchPanel(
    initial: MatchSettings,
    onStart: (MatchSettings) -> Unit,
    onEndSession: () -> Unit,
) {
    var settings by remember(initial) { mutableStateOf(initial) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
    ) {
        Text("Next match", style = MaterialTheme.typography.titleLarge)
        MatchSettingsPicker(settings, { settings = it })
        Button(
            onClick = { onStart(settings) },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) { Text("Start match", style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) { Text("End session") }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val previewMatch = Match(
    id = "m",
    number = 3,
    settings = MatchSettings(GameType.EIGHT_BALL, raceTo = 5),
    status = Status.ACTIVE,
    frameWins = mapOf("a" to 4, "b" to 2),
)

@Preview(heightDp = 800)
@Composable
private fun SessionContentPreview() {
    RivalsTheme {
        SessionContent(
            SessionUiState(
                loading = false,
                me = PlayerSide("a", "Kevin", frames = 4, matches = 2),
                rival = PlayerSide("b", "Dave", frames = 2, matches = 1),
                match = previewMatch,
                frameWinners = listOf("Kevin", "Dave", "Kevin", "Kevin", "Dave", "Kevin"),
                breakerId = "b",
                canUndo = true,
                pendingSync = true,
            ),
            SessionActions(),
        )
    }
}

@Preview(heightDp = 800)
@Composable
private fun BetweenMatchesPreview() {
    RivalsTheme {
        SessionContent(
            SessionUiState(
                loading = false,
                me = PlayerSide("a", "Kevin", frames = 0, matches = 2),
                rival = PlayerSide("b", "Dave", frames = 0, matches = 1),
                canUndo = true,
            ),
            SessionActions(),
        )
    }
}
