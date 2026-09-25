package com.kevinbevan.rivals.ui.session

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kevinbevan.rivals.ui.components.Chip
import com.kevinbevan.rivals.ui.components.IconAction
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.Pips
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.SecondaryButton
import com.kevinbevan.rivals.ui.components.TextAction
import com.kevinbevan.rivals.ui.rememberMatchDefaults
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Motion
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun SessionScreen(
    onExit: () -> Unit,
    onHome: () -> Unit = onExit,
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
            onBack = onHome,
            onRecordFrame = viewModel::recordFrame,
            onToggleEvent = viewModel::toggleEvent,
            onUndo = viewModel::undo,
            onEndMatch = viewModel::endMatch,
            onStartMatch = viewModel::startMatch,
            onChangeSettings = viewModel::changeSettings,
            onEndSession = viewModel::endSession,
            onDismissResult = viewModel::dismissResult,
            onMessageShown = viewModel::dismissMessage,
        ),
    )
}

/**
 * While a game is on screen: landscape (either way up), the status bar hidden, and the screen
 * kept awake since the phone sits on the table between shots. The navigation bar stays, so one
 * swipe up still leaves the app and a swipe in from the side still goes back; hidden, the first
 * swipe only brought the bar in and the phone had to be fought to get out.
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
        bars.hide(WindowInsetsCompat.Type.statusBars())
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            bars.show(WindowInsetsCompat.Type.statusBars())
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
    val onDismissResult: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

private enum class SessionDialog { CHANGE_SETTINGS, END_MATCH, END_SESSION }

/** The status line along the foot of the board. Nothing sits on the centre line. */
private val StatusLineHeight = 52.dp

/** How long the match-won panel holds the board before the next match gets on with it. */
private const val ResultPanelMillis = 9_000L

/**
 * The scoreboard: charcoal field, each player owns half of it, and tapping their half records a
 * frame for them. Their name and race pips carry their colour; the score is always white.
 * Everything that isn't scoring lives behind the menu in the corner of the status line.
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
    val result = uiState.justWon
    // Held for the length of the fade-out, so the panel still has something to draw.
    var lastResult by remember { mutableStateOf(result) }
    LaunchedEffect(result) { if (result != null) lastResult = result }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(result?.matchId) {
        if (result != null) {
            // A heavier thump than a frame: the match is over.
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(ResultPanelMillis)
            actions.onDismissResult()
        }
    }

    Box(Modifier.fillMaxSize().background(Rivals.colors.base)) {
        when {
            uiState.loading || me == null || rival == null ->
                Label("Opening the scoreboard…", Modifier.align(Alignment.Center))

            match != null -> {
                // The board dims behind the match-won panel so the panel is the only thing to
                // read, and dims back when the next match takes over.
                val dim by animateFloatAsState(
                    if (result != null) 0.3f else 1f,
                    Motion.tween(Motion.NORMAL),
                    label = "boardDim",
                )
                Row(Modifier.fillMaxSize().alpha(dim)) {
                    ScoreHalf(
                        side = me,
                        color = Rivals.colors.you,
                        tint = Rivals.colors.youTint,
                        raceTo = match.settings.raceTo,
                        enabled = result == null,
                        onClick = { actions.onRecordFrame(me.uid) },
                        modifier = Modifier.weight(1f).testTag("score-${me.uid}"),
                    )
                    ScoreHalf(
                        side = rival,
                        color = Rivals.colors.rival,
                        tint = Rivals.colors.rivalTint,
                        raceTo = match.settings.raceTo,
                        enabled = result == null,
                        onClick = { actions.onRecordFrame(rival.uid) },
                        modifier = Modifier.weight(1f).testTag("score-${rival.uid}"),
                    )
                }
                if (result != null) {
                    // The dimmed board is the panel's backdrop, not a scoreboard: a tap anywhere
                    // on it gets on with the next match rather than waiting the panel out.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = actions.onDismissResult,
                            )
                            .testTag("result-backdrop"),
                    )
                }
                AnimatedVisibility(
                    visible = result != null,
                    enter = fadeIn(Motion.tween(Motion.NORMAL)),
                    exit = fadeOut(Motion.tween(Motion.FAST)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    // Kept after the result clears so the panel can fade rather than vanish.
                    val shown = result ?: lastResult ?: return@AnimatedVisibility
                    MatchWonPanel(
                        result = shown,
                        youWon = shown.winnerId == me.uid,
                        canUndo = uiState.canUndo,
                        onPlayOn = actions.onDismissResult,
                        onUndo = { actions.onDismissResult(); actions.onUndo() },
                    )
                }
                StatusLine(
                    uiState = uiState,
                    match = match,
                    actions = actions,
                    onDialog = { dialog = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            else -> NextMatchPanel(
                uiState = uiState,
                onStart = actions.onStartMatch,
                onUndo = actions.onUndo,
                onEndSession = { dialog = SessionDialog.END_SESSION },
                onBack = actions.onBack,
            )
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 72.dp),
        )
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

/**
 * One player's half. The whole area is the tap target; inside it, their name in their colour,
 * the white score sized to about 45% of the height, their race pips, and the hill chip.
 */
@Composable
private fun ScoreHalf(
    side: PlayerSide,
    color: Color,
    tint: Color,
    raceTo: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // One crisp haptic per score change, so it fires when the rival's phone records the frame
    // too. Not on the first composition: reopening the board mid-match shouldn't buzz.
    var lastFrames by remember(side.uid) { mutableStateOf(side.frames) }
    LaunchedEffect(side.uid, side.frames) {
        if (side.frames != lastFrames) {
            lastFrames = side.frames
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    val onTheHill = raceTo != null && side.frames == raceTo - 1
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            // Padded before the tap target, not inside it: a finger on the status line or
            // swiping up out of the app from the bottom edge mustn't record a frame.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.union(WindowInsets.systemGestures)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(bottom = StatusLineHeight)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Record a frame for ${side.name}",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Sized from the space, not the font scale: it's a scoreboard, not body text.
        val numberHeight = maxHeight * ScoreShare
        val numberSize = with(LocalDensity.current) { numberHeight.toSp() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                side.name.uppercase(),
                style = Rivals.type.label.copy(fontSize = 20.sp),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.s16),
            )
            RollingScore(side.frames, fontSize = numberSize, emSize = numberHeight)
            Pips(won = side.frames, raceTo = raceTo, color = color)
            // The chip keeps its space either way, so the score doesn't jump on to the hill.
            if (onTheHill) {
                Chip("On the hill", color = color, background = tint)
            } else {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The score's share of the half's height, and its weight: big, but not a slab. */
private const val ScoreShare = 0.52f
private val ScoreWeight = FontWeight.Bold

/**
 * How much of its font size a row of Montserrat digits actually fills: the cap height from the
 * font's own metrics (0.70), with a little slack. Everything above and below that in the line
 * box is empty, which is what would otherwise push the name and pips away from the score.
 */
private const val DigitHeight = 0.72f

/**
 * The one thing on the board that moves: the score rolls up when it goes up, down on an undo.
 *
 * The box is the height of the digits themselves rather than the font's taller line box, so
 * the name and the pips sit as close to the number as they do in the mock-ups.
 */
@Composable
private fun RollingScore(frames: Int, fontSize: TextUnit, emSize: Dp) {
    val style = Rivals.type.score.copy(fontSize = fontSize, lineHeight = fontSize, fontWeight = ScoreWeight)
    Box(
        modifier = Modifier.height(emSize * DigitHeight).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = frames,
            transitionSpec = {
                val up = targetState > initialState
                val enter = slideInVertically(Motion.tween()) { h -> if (up) h else -h } +
                    fadeIn(Motion.tween())
                val exit = slideOutVertically(Motion.tween()) { h -> if (up) -h else h } +
                    fadeOut(Motion.tween())
                enter togetherWith exit
            },
            label = "score",
            modifier = Modifier.wrapContentHeight(unbounded = true),
        ) { value ->
            Text("$value", style = style, color = Rivals.colors.fg, maxLines = 1)
        }
    }
}

/**
 * The quiet line along the bottom: the match clock, then what's being played and tonight's
 * score, with the menu in the corner. Everything else on the board is score.
 */
@Composable
private fun StatusLine(
    uiState: SessionUiState,
    match: Match,
    actions: SessionActions,
    onDialog: (SessionDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = uiState.me ?: return
    val rival = uiState.rival ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .height(StatusLineHeight)
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        IconAction(R.drawable.ic_arrow_back, "Back to home", actions.onBack)
        MatchClock(match.startedAt)
        if (uiState.pendingSync) {
            Icon(
                painterResource(R.drawable.ic_cloud_upload),
                contentDescription = "Saved on this phone, waiting to sync",
                tint = Rivals.colors.fg3,
                modifier = Modifier.size(16.dp),
            )
        }
        Label(
            "Match ${match.number} · ${match.settings.describe()} · " +
                "Tonight ${me.matches} – ${rival.matches}",
            color = Rivals.colors.fg3,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        GameMenu(uiState = uiState, match = match, actions = actions, onDialog = onDialog)
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
        style = Rivals.type.number.copy(fontSize = 16.sp),
        color = Rivals.colors.fg,
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

/**
 * The match is won: the board dims and this says who took it and what happens next. Play on
 * (or a tap anywhere on the dimmed board) hands the board to the next match; Undo is the way
 * back if the wrong half was tapped.
 */
@Composable
private fun MatchWonPanel(
    result: MatchResult,
    youWon: Boolean,
    canUndo: Boolean,
    onPlayOn: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            // Wide enough that the winner's line stays on one line beside the buttons.
            .widthIn(max = 680.dp)
            .padding(horizontal = Space.s24)
            .background(Rivals.colors.surface, Shapes.panel)
            .padding(horizontal = 28.dp, vertical = Space.s24)
            .testTag("match-won"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s24),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(
                "Match ${result.number}" + (result.gameLabel?.let { " · $it" } ?: ""),
                color = if (youWon) Rivals.colors.you else Rivals.colors.rival,
            )
            Text(
                "${result.winnerName} takes it ${result.winnerFrames} – ${result.loserFrames}",
                style = Rivals.type.headline.copy(fontSize = 30.sp, lineHeight = 36.sp),
                color = Rivals.colors.fg,
            )
            Text(result.next, style = Rivals.type.body, color = Rivals.colors.fg2)
        }
        Column(
            modifier = Modifier.width(150.dp),
            verticalArrangement = Arrangement.spacedBy(Space.s8),
        ) {
            PrimaryButton("Play on", onPlayOn)
            if (canUndo) SecondaryButton("Undo", onUndo, Modifier.fillMaxWidth())
        }
    }
}

/** Everything that isn't scoring, behind the one button in the corner. */
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
        IconAction(R.drawable.ic_menu, "Game menu", { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Rivals.colors.surface,
            shape = Shapes.panel,
        ) {
            val tagged = uiState.lastFrameEvents
            if (tagged != null) {
                Label(
                    "Last frame",
                    color = Rivals.colors.fg3,
                    modifier = Modifier.padding(horizontal = Space.s16, vertical = Space.s8),
                )
                FrameEvent.entries.forEach { event ->
                    DropdownMenuItem(
                        text = { Text(event.label, style = Rivals.type.body, color = Rivals.colors.fg) },
                        leadingIcon = {
                            if (event in tagged) {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    contentDescription = "Tagged",
                                    tint = Rivals.colors.fg,
                                )
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                        onClick = { open = false; actions.onToggleEvent(event) },
                    )
                }
            }
            MenuItem("Undo last frame", R.drawable.ic_undo, enabled = uiState.canUndo) {
                open = false
                actions.onUndo()
            }
            if (match.framesPlayed == 0) {
                MenuItem("Change game") { open = false; onDialog(SessionDialog.CHANGE_SETTINGS) }
            } else {
                MenuItem("End match") { open = false; onDialog(SessionDialog.END_MATCH) }
            }
            MenuItem("End session") { open = false; onDialog(SessionDialog.END_SESSION) }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    iconRes: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text,
                style = Rivals.type.body,
                color = if (enabled) Rivals.colors.fg else Rivals.colors.fg3,
            )
        },
        leadingIcon = {
            if (iconRes != null) {
                Icon(painterResource(iconRes), contentDescription = null, tint = Rivals.colors.fg2)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
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
    val defaults = rememberMatchDefaults()
    var settings by remember(uiState.lastSettings) { mutableStateOf(uiState.lastSettings) }
    val me = uiState.me
    val rival = uiState.rival
    // Centred when it fits, scrolling from the top when landscape leaves too little height.
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
    Row(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(horizontal = Space.s24, vertical = Space.s16),
        horizontalArrangement = Arrangement.spacedBy(Space.s32),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s16)) {
            Label("Tonight")
            if (me != null && rival != null) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Space.s12),
                ) {
                    Text("${me.matches}", style = Rivals.type.display, color = Rivals.colors.fg)
                    Text(
                        "–",
                        style = Rivals.type.display.copy(fontSize = 24.sp),
                        color = Rivals.colors.hairline,
                        modifier = Modifier.padding(bottom = Space.s8),
                    )
                    Text("${rival.matches}", style = Rivals.type.display, color = Rivals.colors.fg)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label(me.name, color = Rivals.colors.you)
                    Label(rival.name, color = Rivals.colors.rival)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s12)) {
            Label("Next match")
            MatchSettingsPicker(settings, { settings = it })
            PrimaryButton("Start match", {
                defaults.last = settings
                onStart(settings)
            })
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction("End session", onEndSession)
                Spacer(Modifier.weight(1f))
                if (uiState.canUndo) TextAction("Undo last frame", onUndo)
                TextAction("Home", onBack)
            }
        }
    }
    }
}

private val previewMatch = Match(
    id = "m",
    number = 3,
    settings = MatchSettings(GameType.NINE_BALL, raceTo = 5),
    status = Status.ACTIVE,
    frameWins = mapOf("a" to 4, "b" to 2),
)

@Preview(widthDp = 915, heightDp = 412)
@Composable
private fun SessionContentPreview() {
    RivalsTheme {
        SessionContent(
            SessionUiState(
                loading = false,
                me = PlayerSide("a", "Kevin", frames = 4, matches = 2),
                rival = PlayerSide("b", "Julian", frames = 2, matches = 1),
                match = previewMatch,
                lastFrameEvents = setOf(FrameEvent.BREAK_AND_RUN),
                canUndo = true,
                pendingSync = true,
            ),
            SessionActions(),
        )
    }
}

@Preview(widthDp = 915, heightDp = 412)
@Composable
private fun BetweenMatchesPreview() {
    RivalsTheme {
        SessionContent(
            SessionUiState(
                loading = false,
                me = PlayerSide("a", "Kevin", frames = 0, matches = 2),
                rival = PlayerSide("b", "Julian", frames = 0, matches = 1),
                canUndo = true,
            ),
            SessionActions(),
        )
    }
}
