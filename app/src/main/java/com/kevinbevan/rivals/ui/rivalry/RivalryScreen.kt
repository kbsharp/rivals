package com.kevinbevan.rivals.ui.rivalry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.components.ChoiceRow
import com.kevinbevan.rivals.ui.components.IconAction
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.LoadingState
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.RivalsTextField
import com.kevinbevan.rivals.ui.components.Tabs
import com.kevinbevan.rivals.ui.components.TopBar
import com.kevinbevan.rivals.ui.components.WinRing
import com.kevinbevan.rivals.ui.session.ConfirmDialog
import com.kevinbevan.rivals.ui.rememberMatchDefaults
import com.kevinbevan.rivals.ui.session.MatchSettingsPicker
import com.kevinbevan.rivals.ui.session.RivalsDialog
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space

@Composable
fun RivalryScreen(
    onOpenSession: (String) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RivalryViewModel = viewModel(factory = RivalryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.openSessionId) {
        uiState.openSessionId?.let {
            viewModel.onSessionOpened()
            onOpenSession(it)
        }
    }
    LaunchedEffect(uiState.gone) { if (uiState.gone) onBack() }

    RivalryContent(
        uiState = uiState,
        onStartSession = viewModel::startSession,
        onResumeSession = onOpenSession,
        onOpenSessionDetail = onOpenSessionDetail,
        onRemove = viewModel::remove,
        onBack = onBack,
        onErrorShown = viewModel::dismissError,
    )
}

/**
 * One rival, whole: the win ring beside the all-time score, the one action worth taking, and
 * the nights you've played and the stats behind them as two tabs. History and Stats used to be
 * screens of their own; nothing was on them that doesn't belong here.
 */
@Composable
internal fun RivalryContent(
    uiState: RivalryUiState,
    onStartSession: (MatchSettings, String?) -> Unit,
    onResumeSession: (String) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    onErrorShown: () -> Unit,
    /** Which tab opens first. Only the screenshot renders pass anything but the default. */
    initialTab: RivalryTab = RivalryTab.SESSIONS,
) {
    var choosingSettings by rememberSaveable { mutableStateOf(false) }
    var confirmingRemove by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Box(Modifier.fillMaxSize().background(Rivals.colors.base)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.s24),
            verticalArrangement = Arrangement.spacedBy(Space.section),
        ) {
            RivalryTopBar(uiState, onBack) { confirmingRemove = true }

            if (uiState.loading) {
                LoadingState("Opening the head to head")
                return@Column
            }

            Header(uiState)
            PrimaryAction(uiState, onResumeSession) { choosingSettings = true }

            Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
                Tabs(
                    titles = RivalryTab.entries.map { it.label },
                    selectedIndex = tab.ordinal,
                    onSelect = { tab = RivalryTab.entries[it] },
                )
                when (tab) {
                    RivalryTab.SESSIONS -> SessionsTab(uiState, onOpenSessionDetail)
                    RivalryTab.STATS -> StatsTab(uiState)
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (choosingSettings) {
        NewSessionDialog(
            recentVenues = uiState.recentVenues,
            onStart = { settings, venue ->
                choosingSettings = false
                onStartSession(settings, venue)
            },
            onDismiss = { choosingSettings = false },
        )
    }
    if (confirmingRemove) {
        ConfirmDialog(
            title = "Remove ${uiState.rivalName}?",
            text = "You won't be able to start sessions together until one of you invites the " +
                "other again. Past sessions stay in your history.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { confirmingRemove = false; onRemove() },
            onDismiss = { confirmingRemove = false },
        )
    }
}

@Composable
private fun RivalryTopBar(uiState: RivalryUiState, onBack: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopBar(uiState.rivalName, onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.pendingSync) {
                Icon(
                    painterResource(R.drawable.ic_cloud_upload),
                    contentDescription = "Waiting to sync",
                    tint = Rivals.colors.fg3,
                    modifier = Modifier.padding(horizontal = Space.s8).size(16.dp),
                )
            }
            Box {
                IconAction(R.drawable.ic_more_vert, "More", { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Rivals.colors.surface,
                    shape = Shapes.panel,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Remove rival",
                                style = Rivals.type.body,
                                color = Rivals.colors.live,
                            )
                        },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

/** The ring and the all-time score: how the rivalry stands, in one look. */
@Composable
private fun Header(uiState: RivalryUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WinRing(yourWins = uiState.myWins, rivalWins = uiState.rivalWins)
        Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Label("All time")
            Text(
                "${uiState.myWins} – ${uiState.rivalWins}",
                style = Rivals.type.display.copy(fontSize = 44.sp, lineHeight = 46.sp),
                color = Rivals.colors.fg,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("You", color = Rivals.colors.you)
                Label(uiState.rivalName, color = Rivals.colors.rival)
            }
            Text(
                streakLine(uiState),
                style = Rivals.type.caption,
                color = Rivals.colors.fg3,
            )
        }
    }
}

/** "Julian has won the last 2", or what to expect when nothing has been played. */
private fun streakLine(uiState: RivalryUiState): String {
    val stats = uiState.stats ?: return "Matches won"
    val streak = stats.currentStreak
    val name = if (streak.playerId == stats.myId) "You have" else "${uiState.rivalName} has"
    return when {
        streak.playerId == null -> "No matches finished yet"
        streak.length == 1 -> "$name won the last one"
        else -> "$name won the last ${streak.length}"
    }
}

/** The screen's one action: resume tonight, or start a night. */
@Composable
private fun PrimaryAction(
    uiState: RivalryUiState,
    onResumeSession: (String) -> Unit,
    onStart: () -> Unit,
) {
    val active = uiState.activeSession
    Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        when {
            active != null -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Label("Tonight", modifier = Modifier.weight(1f))
                    Text(
                        "${active.myWins} – ${active.rivalWins}",
                        style = Rivals.type.number.copy(fontSize = 20.sp),
                        color = Rivals.colors.fg,
                    )
                }
                PrimaryButton("Resume session", { onResumeSession(active.id) })
            }
            uiState.starting -> LoadingState("Starting the session")
            else -> {
                PrimaryButton("Start session", onStart, enabled = uiState.accepted)
                if (!uiState.accepted) {
                    Text(
                        "${uiState.rivalName} needs to accept your invite before you can start " +
                            "a session.",
                        style = Rivals.type.body,
                        color = Rivals.colors.fg2,
                    )
                }
            }
        }
    }
}

/** Match settings for the first match, plus an optional venue. */
@Composable
private fun NewSessionDialog(
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

@Preview(heightDp = 900)
@Composable
private fun RivalryContentPreview() {
    RivalsTheme {
        RivalryContent(
            RivalryUiState(
                loading = false,
                myName = "Kevin",
                rivalName = "Julian",
                myWins = 12,
                rivalWins = 9,
            ),
            { _, _ -> }, {}, {}, {}, {}, {},
        )
    }
}
