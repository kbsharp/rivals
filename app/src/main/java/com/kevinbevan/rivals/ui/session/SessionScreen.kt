package com.kevinbevan.rivals.ui.session

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.kevinbevan.rivals.ui.components.ToggleChip
import com.kevinbevan.rivals.ui.rememberMatchDefaults
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Motion
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * The scoreboard. [onHome] is every way off it: the arrow, system back, a night deleted
 * because nothing was played, and *Done* at full time. [onSeeNight] opens the night's detail
 * from the full-time panel.
 */
@Composable
fun SessionScreen(
    onHome: () -> Unit,
    onSeeNight: () -> Unit = onHome,
    viewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.ended) {
        if (uiState.ended) onHome()
    }
    // Back means one thing on the board: Home, with the night still running.
    BackHandler(onBack = onHome)

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
            onDismissNotice = viewModel::dismissNotice,
            onResultShown = viewModel::resultShown,
            onSeeNight = onSeeNight,
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
    val onDismissNotice: (Notice) -> Unit = {},
    val onResultShown: (String) -> Unit = {},
    val onSeeNight: () -> Unit = {},
)

private enum class SessionDialog { END_MATCH, END_SESSION }

/** The height of the lines along the top and foot of the board. Nothing sits on the centre line. */
private val StatusLineHeight = 52.dp

/**
 * [WindowInsets.safeDrawing] with the wider of its left and right insets on both sides. In
 * landscape the camera cutout insets one side only, which pushes back in from the edge while
 * ≡ sits hard against the curved glass on the other; the lines along the top and foot mirror it.
 */
@Composable
private fun mirroredSafeDrawing(): WindowInsets {
    val safe = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val side = maxOf(safe.getLeft(density, LayoutDirection.Ltr), safe.getRight(density, LayoutDirection.Ltr))
    return safe.union(WindowInsets(left = side, right = side))
}

/** How long the match-won panel holds the board before the next match gets on with it. */
private const val ResultPanelMillis = 9_000L

/** How long a notice has the foot of the board: what an undo took back, or what went wrong. */
private const val NoticeMillis = 4_000L
private const val WarningMillis = 6_000L

/**
 * The scoreboard: charcoal field, each player owns half of it, and tapping their half records a
 * frame for them. Their name and race pips carry their colour; the score is always white.
 *
 * Every other action lives on the thing it acts on (brief, "Where actions live"): the last
 * frame's Undo sits by the clock and its tags on its receipt at the foot of the board, the
 * match sheet behind ≡ holds the match and the night, and the match-won panel sets the next
 * match. Messages take the receipt's place, never a snackbar over the board.
 */
@Composable
internal fun SessionContent(uiState: SessionUiState, actions: SessionActions) {
    var dialog by rememberSaveable { mutableStateOf<SessionDialog?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var tagsOpen by rememberSaveable { mutableStateOf(false) }
    val notice = uiState.notice
    LaunchedEffect(notice?.key) {
        if (notice != null) {
            delay(if (notice.warning) WarningMillis else NoticeMillis)
            actions.onDismissNotice(notice)
        }
    }
    val match = uiState.match
    val me = uiState.me
    val rival = uiState.rival
    val result = uiState.justWon
    val receipt = uiState.lastFrame
    // A new last frame (from either phone), or none, closes the tags that were open on the old one.
    LaunchedEffect(receipt?.number, receipt?.winnerId, notice != null) { if (receipt == null || notice != null) tagsOpen = false }
    LaunchedEffect(match == null) { if (match == null) sheetOpen = false }
    // Held for the length of the fade-out, so the panel still has something to draw.
    var lastResult by remember { mutableStateOf(result) }
    LaunchedEffect(result) { if (result != null) lastResult = result }
    // Once the panel has been touched (a tag, the next match's settings) it waits for Play on.
    var held by remember(result?.matchId) { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(result?.matchId) {
        if (result != null) {
            // A heavier thump than a frame: the match is over.
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            actions.onResultShown(result.matchId)
        }
    }
    LaunchedEffect(result?.matchId, held) {
        if (result != null && !held) {
            delay(ResultPanelMillis)
            actions.onDismissResult()
        }
    }
    val defaults = rememberMatchDefaults()
    val changeSettings = { settings: MatchSettings ->
        defaults.last = settings
        actions.onChangeSettings(settings)
    }

    Box(Modifier.fillMaxSize().background(Rivals.colors.base)) {
        val fullTime = uiState.fullTime
        if (uiState.loading || me == null || rival == null) {
            Label("Opening the scoreboard…", Modifier.align(Alignment.Center))
        } else if (fullTime != null) {
            // Full time: the last match stays on the dimmed board, and the panel tells the night.
            Row(Modifier.fillMaxSize().alpha(0.3f)) {
                ScoreHalf(me, Rivals.colors.you, Rivals.colors.youTint, fullTime.lastMatch?.settings?.raceTo, false, {}, Modifier.weight(1f))
                ScoreHalf(rival, Rivals.colors.rival, Rivals.colors.rivalTint, fullTime.lastMatch?.settings?.raceTo, false, {}, Modifier.weight(1f))
            }
            FullTimePanel(
                fullTime = fullTime,
                youId = me.uid,
                onDone = actions.onBack,
                onSeeNight = actions.onSeeNight,
                modifier = Modifier.align(Alignment.Center).windowInsetsPadding(WindowInsets.safeDrawing),
            )
        } else {
            if (match != null) {
                // The board dims behind a panel or the sheet so it's the only thing to read.
                val dim by animateFloatAsState(
                    if (result != null || sheetOpen) 0.3f else 1f,
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
                    // on it gets on with the next match, unless the panel is being used.
                    Backdrop("result-backdrop") { if (!held) actions.onDismissResult() }
                }
                AnimatedVisibility(
                    visible = result != null,
                    enter = fadeIn(Motion.tween(Motion.NORMAL)),
                    exit = fadeOut(Motion.tween(Motion.FAST)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(vertical = StatusLineHeight),
                ) {
                    // Kept after the result clears so the panel can fade rather than vanish.
                    val shown = result ?: lastResult ?: return@AnimatedVisibility
                    MatchWonPanel(
                        result = shown,
                        youWon = shown.winnerId == me.uid,
                        canUndo = uiState.canUndo,
                        next = match.settings,
                        winningFrame = receipt?.takeIf { match.framesPlayed == 0 },
                        onTouched = { held = true },
                        onChangeNext = changeSettings,
                        onToggleTag = actions.onToggleEvent,
                        onPlayOn = actions.onDismissResult,
                        onUndo = { actions.onDismissResult(); actions.onUndo() },
                    )
                }
            } else {
                NextMatchPanel(
                    uiState = uiState,
                    onStart = actions.onStartMatch,
                    onUndo = actions.onUndo,
                )
            }
            TopBar(
                uiState = uiState,
                onBack = actions.onBack,
                onUndo = actions.onUndo,
                onMenu = { if (match != null) sheetOpen = true else dialog = SessionDialog.END_SESSION },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            FootLine(
                uiState = uiState,
                tagsOpen = tagsOpen,
                onReceipt = { tagsOpen = !tagsOpen },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (tagsOpen && receipt != null) {
                Backdrop("tags-backdrop") { tagsOpen = false }
                TagRow(
                    receipt = receipt,
                    youId = me.uid,
                    // One tag is the usual job, so choosing it closes the row.
                    onToggle = { actions.onToggleEvent(it); tagsOpen = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(bottom = StatusLineHeight + Space.s4),
                )
            }
            if (sheetOpen && match != null) {
                Backdrop("sheet-backdrop") { sheetOpen = false }
                MatchSheet(
                    match = match,
                    me = me,
                    rival = rival,
                    onChange = changeSettings,
                    onEndMatch = { sheetOpen = false; dialog = SessionDialog.END_MATCH },
                    onEndSession = { sheetOpen = false; dialog = SessionDialog.END_SESSION },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(mirroredSafeDrawing().only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                        .padding(start = Space.s48, end = Space.s48, top = StatusLineHeight + Space.s4),
                )
            }
        }
    }

    when (dialog) {
        SessionDialog.END_MATCH -> ConfirmDialog(
            title = "End this match?",
            text = match?.let { endMatchConsequence(it, uiState) }.orEmpty(),
            confirmLabel = "End match",
            onConfirm = { dialog = null; actions.onEndMatch() },
            onDismiss = { dialog = null },
        )
        SessionDialog.END_SESSION -> ConfirmDialog(
            title = "End this session?",
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

/** A clear layer over the board: a tap on it closes what's open instead of recording a frame. */
@Composable
private fun Backdrop(tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .testTag(tag),
    )
}

/** What ending [match] by hand will mean, per [ScoreRules.manualWinner]. */
private fun endMatchConsequence(match: Match, uiState: SessionUiState): String {
    if (match.settings.raceTo != null) {
        return "Nobody has reached ${match.settings.raceTo} yet, so it won't count for either of you."
    }
    val winner = ScoreRules.manualWinner(match)
        ?: return "It's tied, so it won't count for either of you."
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
            // Padded before the tap target, not inside it: a finger on the top or foot line or
            // swiping up out of the app from the bottom edge mustn't record a frame.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.union(WindowInsets.systemGestures)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(vertical = StatusLineHeight)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "Record a rack for ${side.name}",
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
 * The line along the top, read from the rail: back and what's being played on the left,
 * tonight's score and ≡ on the right, and the match clock in the middle with Undo beside it.
 * The clock stays centred whether or not Undo is there, and Undo is kept away from back.
 */
@Composable
private fun TopBar(
    uiState: SessionUiState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = uiState.me ?: return
    val rival = uiState.rival ?: return
    val match = uiState.match
    // The match-won and between-matches panels have their own Undo.
    val undo = uiState.lastFrame?.takeIf { uiState.canUndo && match != null && uiState.justWon == null }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(mirroredSafeDrawing().only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .height(StatusLineHeight)
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The two sides share the width equally, so the clock sits on the centre of the screen.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s12),
        ) {
            IconAction(R.drawable.ic_arrow_back, "Back to home", onBack)
            if (match != null) {
                Text(
                    "Match ${match.number} · ${match.settings.describe()}".uppercase(),
                    style = Rivals.type.status,
                    color = Rivals.colors.fg2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = Space.s16),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s8),
        ) {
            // An empty slot on the left the width of Undo keeps the clock centred.
            Spacer(Modifier.size(Space.touch))
            if (match != null) MatchClock(match.startedAt)
            if (undo != null) {
                UndoButton(undo.number, onUndo)
            } else {
                Spacer(Modifier.size(Space.touch))
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s16, Alignment.End),
        ) {
            // Between matches the panel leads with tonight's score, so it isn't repeated here.
            if (match != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("Session", maxLines = 1)
                Text(
                    "${me.matches} – ${rival.matches}",
                    style = Rivals.type.number.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                    color = Rivals.colors.fg,
                    maxLines = 1,
                    modifier = Modifier.testTag("tonight"),
                )
            }
            IconAction(R.drawable.ic_menu, "Game menu", onMenu)
        }
    }
}

/** Takes the last frame back: one tap, unconfirmed, on both phones. */
@Composable
private fun UndoButton(frame: Int, onUndo: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Space.touch)
            .clickable(role = Role.Button, onClickLabel = "Undo rack $frame", onClick = onUndo)
            .testTag("undo"),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).background(Rivals.colors.raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_undo),
                contentDescription = "Undo rack $frame",
                tint = Rivals.colors.fg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The foot of the board holds one thing, centred: the last frame's receipt, or a notice in its
 * place. The unsynced cloud has a slot of its own beside it, kept whether it shows or not, so
 * nothing moves when it comes and goes.
 */
@Composable
private fun FootLine(
    uiState: SessionUiState,
    tagsOpen: Boolean,
    onReceipt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val me = uiState.me ?: return
    val notice = uiState.notice
    // A frame's receipt belongs to a match on the board; between matches there's none to tag.
    val receipt = uiState.lastFrame?.takeIf { uiState.canUndo && uiState.match != null }
    val cloud by animateFloatAsState(if (uiState.pendingSync) 1f else 0f, Motion.tween(Motion.NORMAL), label = "cloud")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(mirroredSafeDrawing().only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .height(StatusLineHeight)
            .padding(horizontal = Space.s48),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s8, Alignment.CenterHorizontally),
    ) {
        // Matches the cloud's slot, so the receipt is centred on the screen.
        Spacer(Modifier.size(CloudSlot))
        when {
            notice != null -> NoticePill(notice, Modifier.weight(1f, fill = false))
            receipt != null -> Receipt(
                receipt = receipt,
                youId = me.uid,
                open = tagsOpen,
                onClick = onReceipt,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Box(Modifier.size(CloudSlot).alpha(cloud), contentAlignment = Alignment.Center) {
            if (uiState.pendingSync) {
                Icon(
                    painterResource(R.drawable.ic_cloud_upload),
                    contentDescription = "Saved on this phone, waiting to sync",
                    tint = Rivals.colors.fg3,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private val CloudSlot = 20.dp

/**
 * The last frame: "RACK 6 · KEVIN", the name in its winner's colour, "· THEIR PHONE" when the
 * other phone recorded it, and its tags. Amber when it looks like one frame recorded on both
 * phones. Tapping it opens the tags above it; Undo is up by the clock.
 */
@Composable
private fun Receipt(
    receipt: FrameReceipt,
    youId: String,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val double = receipt.double
    val text = buildAnnotatedString {
        fun add(part: String) = append(part.uppercase())
        if (double != null) {
            add(if (double.first != null) "Racks ${double.first} & ${receipt.number}" else "Last two racks")
            add(" · ${double.secondsApart} s apart")
        } else {
            add("Rack ${receipt.number} · ")
            withStyle(SpanStyle(color = Rivals.colors.forPlayer(receipt.winnerId, youId), fontWeight = FontWeight.Bold)) {
                add(receipt.winnerName)
            }
            if (receipt.theirPhone) add(" · their phone")
            FrameEvent.entries.filter { it in receipt.events }.forEach { add(" · ${it.label}") }
        }
    }
    Box(
        modifier = modifier
            .heightIn(min = Space.touch)
            .clickable(role = Role.Button, onClickLabel = "Tag rack ${receipt.number}", onClick = onClick)
            .testTag("receipt"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Rivals.type.label.copy(letterSpacing = 0.08.em),
            color = if (double != null) Rivals.colors.live else Rivals.colors.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(if (double != null) Rivals.colors.liveTint else Rivals.colors.raised, Shapes.pill)
                .let { if (open) it.border(2.dp, Rivals.colors.fg3, Shapes.pill) else it }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** A message in the receipt's place: white on `raised`, or amber when something went wrong. */
@Composable
private fun NoticePill(notice: Notice, modifier: Modifier = Modifier) {
    Text(
        notice.text.uppercase(),
        style = Rivals.type.label.copy(letterSpacing = 0.08.em),
        color = if (notice.warning) Rivals.colors.live else Rivals.colors.fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(if (notice.warning) Rivals.colors.liveTint else Rivals.colors.raised, Shapes.pill)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag("notice"),
    )
}

/** The last frame's tags, raised above its receipt. A chosen tag wears the winner's colour. */
@Composable
private fun TagRow(
    receipt: FrameReceipt,
    youId: String,
    onToggle: (FrameEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Rivals.colors.surface, Shapes.panel)
            .padding(Space.s12)
            .testTag("tags"),
        horizontalArrangement = Arrangement.spacedBy(Space.s8),
    ) {
        TagChips(receipt, youId, onToggle)
    }
}

@Composable
private fun TagChips(receipt: FrameReceipt, youId: String, onToggle: (FrameEvent) -> Unit) {
    FrameEvent.entries.forEach { event ->
        ToggleChip(
            text = event.label,
            selected = event in receipt.events,
            onClick = { onToggle(event) },
            color = Rivals.colors.forPlayer(receipt.winnerId, youId),
            tint = Rivals.colors.tintForPlayer(receipt.winnerId, youId),
        )
    }
}

/**
 * Behind ≡: this match, and the way out of the night. The game and race change the running
 * match at once, on both phones; End match and, set apart, End session are still confirmed.
 */
@Composable
private fun MatchSheet(
    match: Match,
    me: PlayerSide,
    rival: PlayerSide,
    onChange: (MatchSettings) -> Unit,
    onEndMatch: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held here so quick taps on the stepper build on each other, not on a snapshot in flight.
    var settings by remember(match.id) { mutableStateOf(match.settings) }
    LaunchedEffect(match.settings) { settings = match.settings }
    val minRace = ScoreRules.minRace(match)
    val minutes = match.startedAt?.let { Duration.between(it, Instant.now()).toMinutes().coerceAtLeast(0) }
    Column(
        modifier = modifier
            .widthIn(max = 800.dp)
            .fillMaxWidth()
            .background(Rivals.colors.surface, Shapes.panel)
            // Taps between its controls stay on it, rather than reaching the backdrop.
            .pointerInput(Unit) { detectTapGestures() }
            .padding(horizontal = 28.dp, vertical = Space.s24)
            .testTag("match-sheet"),
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
            Text("Match ${match.number}", style = Rivals.type.headline, color = Rivals.colors.fg)
            Label(
                "${me.name} ${me.frames} – ${rival.frames} ${rival.name}" + (minutes?.let { " · $it min" } ?: ""),
                maxLines = 1,
            )
        }
        // End match sits on the game row and End session on the race row, so each lines up
        // with the chips beside it.
        MatchSettingsPicker(
            settings = settings,
            onChange = { settings = it; onChange(it) },
            minRace = minRace,
            gameAction = {
                SheetAction(if (match.framesPlayed > 0) "End match" else null, onEndMatch)
            },
            raceAction = { SheetAction("End session", onEndSession) },
        )
        Text(
            "Changes apply at once." + if (minRace > 1) " The race can't go below $minRace." else "",
            style = Rivals.type.caption,
            color = Rivals.colors.fg2,
        )
    }
}

/** One of the sheet's two ways out, at the end of a picker row; an empty slot keeps the chips' width. */
@Composable
private fun SheetAction(text: String?, onClick: () -> Unit) {
    Spacer(Modifier.width(Space.s24))
    Box(Modifier.width(180.dp).height(Space.touch)) {
        if (text != null) SecondaryButton(text, onClick, Modifier.fillMaxSize())
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
 * The match is won: the board dims and this says who took it and what happens next. The next
 * match is already running, and its settings can be changed here; the winning frame can be
 * tagged. Play on (or a tap on the dimmed board, until the panel's been used) hands the board
 * to the next match; Undo is the way back if the wrong half was tapped.
 */
@Composable
private fun MatchWonPanel(
    result: MatchResult,
    youWon: Boolean,
    canUndo: Boolean,
    next: MatchSettings,
    winningFrame: FrameReceipt?,
    onTouched: () -> Unit,
    onChangeNext: (MatchSettings) -> Unit,
    onToggleTag: (FrameEvent) -> Unit,
    onPlayOn: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var changing by rememberSaveable(result.matchId) { mutableStateOf(false) }
    var settings by remember(result.matchId) { mutableStateOf(next) }
    LaunchedEffect(next) { settings = next }
    Row(
        modifier = modifier
            // Wide enough that the winner's line stays on one line beside the buttons.
            .widthIn(max = 720.dp)
            .padding(horizontal = Space.s24)
            .background(Rivals.colors.surface, Shapes.panel)
            // Taps between its controls stay on it, rather than reaching the backdrop.
            .pointerInput(Unit) { detectTapGestures() }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = Space.s24)
            .testTag("match-won"),
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
            Spacer(Modifier.height(Space.s8))
            if (changing) {
                MatchSettingsPicker(
                    settings = settings,
                    onChange = { settings = it; onTouched(); onChangeNext(it) },
                    gameLabel = "Next game",
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
                    Label("Next")
                    Text(next.describe().replaceFirstChar { it.uppercase() }, style = Rivals.type.rowTitle, color = Rivals.colors.fg)
                    TextAction("Change", { changing = true; onTouched() }, color = Rivals.colors.fg)
                }
                if (winningFrame != null) {
                    Label("Tag the winning rack")
                    Row(Modifier.padding(top = Space.s4), horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                        TagChips(winningFrame, if (youWon) result.winnerId else "") { onTouched(); onToggleTag(it) }
                    }
                }
            }
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

/**
 * The night is over, on both phones: where and how long, who took it, what happened, and what
 * it does to the all-time score. *Done* goes Home; *See the night* opens its detail.
 */
@Composable
private fun FullTimePanel(
    fullTime: FullTime,
    youId: String,
    onDone: () -> Unit,
    onSeeNight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val header = listOfNotNull("Final", fullTime.venue, fullTime.length?.let(::formatLength)).joinToString(" · ")
    Row(
        modifier = modifier
            .widthIn(max = 720.dp)
            .padding(horizontal = Space.s24)
            .background(Rivals.colors.surface, Shapes.panel)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = Space.s24)
            .testTag("full-time"),
        horizontalArrangement = Arrangement.spacedBy(Space.s24),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Label(
                header,
                color = when (fullTime.winnerId) {
                    null -> Rivals.colors.fg3
                    else -> Rivals.colors.forPlayer(fullTime.winnerId, youId)
                },
            )
            Text(
                fullTime.headline,
                style = Rivals.type.headline.copy(fontSize = 28.sp, lineHeight = 34.sp),
                color = Rivals.colors.fg,
            )
            if (fullTime.summary.isNotEmpty()) {
                Text(fullTime.summary, style = Rivals.type.body, color = Rivals.colors.fg2)
            }
            fullTime.allTime?.let { (mine, theirs) ->
                Row(
                    Modifier.padding(top = Space.s12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s16),
                ) {
                    Label("All time")
                    Text(
                        "$mine – $theirs",
                        style = Rivals.type.number.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                        color = Rivals.colors.fg,
                    )
                }
            }
        }
        Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            PrimaryButton("Done", onDone)
            SecondaryButton("See the session", onSeeNight, Modifier.fillMaxWidth())
        }
    }
}

/** "3 h 02 m", or "42 min" under the hour. */
internal fun formatLength(length: Duration): String {
    val minutes = length.toMinutes().coerceAtLeast(0)
    return if (minutes >= 60) "%d h %02d m".format(minutes / 60, minutes % 60) else "$minutes min"
}

/**
 * Between matches (after one was ended by hand): tonight's score as a row per player, with a
 * pip for each match won and the leader's row in their colour, the matches played so far, and
 * the next match to start. Home, notices and End session stay on the top and foot lines.
 */
@Composable
private fun NextMatchPanel(
    uiState: SessionUiState,
    onStart: (MatchSettings) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaults = rememberMatchDefaults()
    var settings by remember(uiState.lastSettings) { mutableStateOf(uiState.lastSettings) }
    val me = uiState.me ?: return
    val rival = uiState.rival ?: return
    val number = uiState.nextMatchNumber
    Row(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = StatusLineHeight, bottom = Space.s24)
            .padding(horizontal = Space.s48)
            .testTag("next-match"),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label("Session")
            TallyRow(me, Rivals.colors.you, Rivals.colors.youTint, leads = me.matches > rival.matches)
            TallyRow(rival, Rivals.colors.rival, Rivals.colors.rivalTint, leads = rival.matches > me.matches)
            if (uiState.playedMatches.isNotEmpty()) {
                Label("Matches", Modifier.padding(top = Space.s12))
                Column {
                    uiState.playedMatches.forEach { PlayedMatchRow(it, me.uid) }
                }
            }
        }
        Column(
            modifier = Modifier.width(360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s16),
        ) {
            MatchSettingsPicker(settings, { settings = it }, gameLabel = "Match $number · game")
            PrimaryButton("Start match $number", {
                defaults.last = settings
                onStart(settings)
            })
            if (uiState.canUndo) {
                TextAction("Undo last rack", onUndo, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** One player's line of tonight's score: name, a pip per match won, the count. */
@Composable
private fun TallyRow(side: PlayerSide, color: Color, tint: Color, leads: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(if (leads) tint else Rivals.colors.surface, Shapes.panel)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            side.name,
            style = Rivals.type.title,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(side.matches.coerceAtMost(MaxPips)) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
            }
        }
        Text(
            "${side.matches}",
            style = Rivals.type.number.copy(fontSize = 36.sp, lineHeight = 40.sp),
            color = Rivals.colors.fg,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 48.dp).testTag("tonight-${side.uid}"),
        )
    }
}

/** Past this many the pips would crowd the name; the number carries the rest. */
private const val MaxPips = 9

/** "1   10-ball · race to 5 · ended early   Lara 3 – 2". */
@Composable
private fun PlayedMatchRow(played: PlayedMatch, youId: String) {
    val detail = listOfNotNull(
        played.settings.gameType?.label,
        played.settings.raceTo?.let { "race to $it" },
        "ended early".takeIf { played.endedEarly && played.settings.raceTo != null },
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }
    val hairline = Rivals.colors.hairline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .drawBehind {
                drawLine(hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "${played.number}",
            style = Rivals.type.number.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            color = Rivals.colors.fg3,
            modifier = Modifier.widthIn(min = 18.dp),
        )
        Text(
            detail,
            style = Rivals.type.body.copy(fontSize = 15.sp),
            color = Rivals.colors.fg2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            played.winnerName ?: "Tied",
            style = Rivals.type.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
            color = if (played.winnerId == null) Rivals.colors.fg2 else Rivals.colors.forPlayer(played.winnerId, youId),
            maxLines = 1,
        )
        Text(
            "${played.score.first} – ${played.score.second}",
            style = Rivals.type.number.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            color = Rivals.colors.fg,
        )
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
                lastFrame = FrameReceipt(6, "a", "Kevin", theirPhone = false, events = setOf(FrameEvent.BREAK_AND_RUN)),
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
                nextMatchNumber = 4,
                playedMatches = listOf(
                    PlayedMatch(3, MatchSettings(GameType.TEN_BALL, raceTo = 5), "a", "Kevin", 3 to 2, endedEarly = true),
                    PlayedMatch(2, MatchSettings(GameType.NINE_BALL, raceTo = 5), "b", "Julian", 5 to 4, endedEarly = false),
                    PlayedMatch(1, MatchSettings(GameType.NINE_BALL, raceTo = 5), "a", "Kevin", 5 to 1, endedEarly = false),
                ),
            ),
            SessionActions(),
        )
    }
}
