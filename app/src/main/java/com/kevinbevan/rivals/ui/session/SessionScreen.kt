package com.kevinbevan.rivals.ui.session

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.theme.LocalPlayerColors
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(
    onExit: () -> Unit,
    viewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.ended) {
        if (uiState.ended) onExit()
    }

    ScoreboardWindow()

    SessionContent(
        uiState = uiState,
        actions = SessionActions(
            onBack = onExit,
            onRecordFrame = viewModel::recordFrame,
            onToggleEvent = viewModel::toggleEvent,
            onUndo = viewModel::undo,
            onEndMatch = viewModel::endMatch,
            onStartMatch = viewModel::startMatch,
            onChangeSettings = viewModel::changeSettings,
            onEndSession = viewModel::endSession,
            onMessageShown = viewModel::dismissMessage,
        ),
    )
}

/**
 * While a game is on screen: landscape (either way up), system bars hidden until swiped in,
 * and the screen kept awake since the phone sits on the table between shots.
 * `MainActivity` handles orientation changes itself, so this doesn't recreate the activity.
 */
@Composable
private fun ScoreboardWindow() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val orientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val bars = WindowCompat.getInsetsController(activity.window, view)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            bars.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = orientation
        }
    }
}

class SessionActions(
    val onBack: () -> Unit = {},
    val onRecordFrame: (String) -> Unit = {},
    val onToggleEvent: (FrameEvent) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onEndMatch: () -> Unit = {},
    val onStartMatch: (MatchSettings) -> Unit = {},
    val onChangeSettings: (MatchSettings) -> Unit = {},
    val onEndSession: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

private enum class SessionDialog { CHANGE_SETTINGS, END_MATCH, END_SESSION }

/**
 * The scoreboard: each player owns half the screen, and tapping it records a frame for them.
 * Everything else (undo, tagging a frame, ending things) lives behind the floating menu.
 */
@Composable
internal fun SessionContent(uiState: SessionUiState, actions: SessionActions) {
    var dialog by rememberSaveable { mutableStateOf<SessionDialog?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val match = uiState.match
    val me = uiState.me
    val rival = uiState.rival

    // A Surface, not a bare background, so text inherits onSurface rather than defaulting to black.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Box(Modifier.fillMaxSize()) {
        when {
            uiState.loading || me == null || rival == null ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            match != null -> {
                val colors = LocalPlayerColors.current
                Row(Modifier.fillMaxSize()) {
                    listOf(
                        Triple(me, colors.me, colors.onMe),
                        Triple(rival, colors.rival, colors.onRival),
                    ).forEach { (side, container, content) ->
                        ScoreHalf(
                            side = side,
                            onTheHill = match.settings.raceTo?.let { side.frames == it - 1 } == true,
                            container = container,
                            content = content,
                            onClick = { actions.onRecordFrame(side.uid) },
                            modifier = Modifier.weight(1f).testTag("score-${side.uid}"),
                        )
                    }
                }
                MatchInfo(
                    uiState = uiState,
                    match = match,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(top = 12.dp),
                )
                GameMenu(
                    uiState = uiState,
                    match = match,
                    actions = actions,
                    onDialog = { dialog = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(bottom = 16.dp),
                )
            }

            else -> NextMatchPanel(
                uiState = uiState,
                onStart = actions.onStartMatch,
                onUndo = actions.onUndo,
                onEndSession = { dialog = SessionDialog.END_SESSION },
                onBack = actions.onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 80.dp),
        )
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

/** One player's half: the whole area is the tap target, the frame count sized to fill it. */
@Composable
private fun ScoreHalf(
    side: PlayerSide,
    onTheHill: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .background(container)
            .clickable(role = Role.Button, onClickLabel = "Record a frame for ${side.name}") {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        // Sized from the space, not the font scale: it's a scoreboard, not body text.
        val numberSize = with(LocalDensity.current) { (maxHeight * 0.45f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                side.frames.toString(),
                color = content,
                fontSize = numberSize,
                lineHeight = numberSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                side.name,
                color = content,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                if (onTheHill) "on the hill" else " ",
                color = content,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** The small pill over the divide: match clock, which match and race, and tonight's score. */
@Composable
private fun MatchInfo(uiState: SessionUiState, match: Match, modifier: Modifier = Modifier) {
    val me = uiState.me ?: return
    val rival = uiState.rival ?: return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        // contentColorFor doesn't recognise the translucent colour, so say it outright.
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MatchClock(match.startedAt)
                if (uiState.pendingSync) {
                    Icon(
                        painterResource(R.drawable.ic_cloud_upload),
                        contentDescription = "Saved on this phone, waiting to sync",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(16.dp),
                    )
                }
            }
            Text(
                "Match ${match.number} · ${match.settings.describe()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tonight ${me.matches} – ${rival.matches}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Time since the match started, ticking each second. Nothing until the start time is known. */
@Composable
private fun MatchClock(startedAt: Instant?) {
    if (startedAt == null) return
    val now by produceState(Instant.now(), startedAt) {
        while (true) {
            value = Instant.now()
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }
    Text(
        formatElapsed(Duration.between(startedAt, now).seconds),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("match-clock"),
    )
}

/** `m:ss`, or `h:mm:ss` from an hour on. Negative (clock skew) reads as zero. */
internal fun formatElapsed(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** The floating button that holds everything that isn't scoring. */
@Composable
private fun GameMenu(
    uiState: SessionUiState,
    match: Match,
    actions: SessionActions,
    onDialog: (SessionDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FloatingActionButton(
            onClick = { open = true },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(painterResource(R.drawable.ic_menu), contentDescription = "Game menu")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val tagged = uiState.lastFrameEvents
            if (tagged != null) {
                Text(
                    "Last frame",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                FrameEvent.entries.forEach { event ->
                    DropdownMenuItem(
                        text = { Text(event.label) },
                        leadingIcon = {
                            if (event in tagged) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = "Tagged")
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                        onClick = { open = false; actions.onToggleEvent(event) },
                    )
                }
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Undo last frame") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_undo), contentDescription = null) },
                enabled = uiState.canUndo,
                onClick = { open = false; actions.onUndo() },
            )
            if (match.framesPlayed == 0) {
                DropdownMenuItem(
                    text = { Text("Change game") },
                    onClick = { open = false; onDialog(SessionDialog.CHANGE_SETTINGS) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("End match") },
                    onClick = { open = false; onDialog(SessionDialog.END_MATCH) },
                )
            }
            DropdownMenuItem(
                text = { Text("End session") },
                onClick = { open = false; onDialog(SessionDialog.END_SESSION) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Back to home") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null) },
                onClick = { open = false; actions.onBack() },
            )
        }
    }
}

/** Between matches (after one was ended by hand): set up the next, laid out for landscape. */
@Composable
private fun NextMatchPanel(
    uiState: SessionUiState,
    onStart: (MatchSettings) -> Unit,
    onUndo: () -> Unit,
    onEndSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settings by remember(uiState.lastSettings) { mutableStateOf(uiState.lastSettings) }
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Next match", style = MaterialTheme.typography.titleLarge)
            val me = uiState.me
            val rival = uiState.rival
            if (me != null && rival != null) {
                Text(
                    "Tonight: ${me.name} ${me.matches} – ${rival.matches} ${rival.name}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            MatchSettingsPicker(settings, { settings = it })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onStart(settings) },
                modifier = Modifier.fillMaxWidth().height(72.dp),
            ) { Text("Start match", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) { Text("End session") }
            if (uiState.canUndo) {
                TextButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text("Undo last frame") }
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to home") }
        }
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

@Preview(widthDp = 840, heightDp = 390)
@Composable
private fun SessionContentPreview() {
    RivalsTheme {
        SessionContent(
            SessionUiState(
                loading = false,
                me = PlayerSide("a", "Kevin", frames = 4, matches = 2),
                rival = PlayerSide("b", "Dave", frames = 2, matches = 1),
                match = previewMatch,
                lastFrameEvents = setOf(FrameEvent.BREAK_AND_RUN),
                canUndo = true,
                pendingSync = true,
            ),
            SessionActions(),
        )
    }
}

@Preview(widthDp = 840, heightDp = 390)
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
