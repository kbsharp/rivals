package com.kevinbevan.rivals.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.components.Avatar
import com.kevinbevan.rivals.ui.components.ChoiceRow
import com.kevinbevan.rivals.ui.components.FormBar
import com.kevinbevan.rivals.ui.components.HeadToHead
import com.kevinbevan.rivals.ui.components.IconAction
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.ListRow
import com.kevinbevan.rivals.ui.components.LiveChip
import com.kevinbevan.rivals.ui.components.LoadingState
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.RivalsTextField
import com.kevinbevan.rivals.ui.components.RowChevron
import com.kevinbevan.rivals.ui.components.RowIcon
import com.kevinbevan.rivals.ui.components.SecondaryButton
import com.kevinbevan.rivals.ui.components.TextAction
import com.kevinbevan.rivals.ui.components.TopBar
import com.kevinbevan.rivals.ui.history.formatDay
import com.kevinbevan.rivals.ui.navigation.SessionRoute
import com.kevinbevan.rivals.ui.rememberMatchDefaults
import com.kevinbevan.rivals.ui.session.MatchSettingsPicker
import com.kevinbevan.rivals.ui.session.RivalsDialog
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Shapes
import com.kevinbevan.rivals.ui.theme.Space

@Composable
fun HomeScreen(
    onSignIn: () -> Unit,
    onOpenRivalry: (String) -> Unit,
    onAddRival: () -> Unit,
    onOpenSession: (SessionRoute) -> Unit,
    onOpenGuestGame: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.openSession) {
        uiState.openSession?.let {
            viewModel.onSessionOpened()
            onOpenSession(it)
        }
    }

    HomeContent(
        uiState = uiState,
        actions = HomeActions(
            onSignIn = onSignIn,
            onSignOut = viewModel::signOut,
            onStartQuickGame = viewModel::startQuickGame,
            onResumeQuickGame = { onOpenSession(SessionRoute(it, guest = true)) },
            onOpenGuestGame = onOpenGuestGame,
            onSaveGuestGame = viewModel::saveGuestGame,
            onOpenRivalry = onOpenRivalry,
            onResumeSession = { onOpenSession(SessionRoute(it, guest = false)) },
            onAddRival = onAddRival,
            onAcceptInvite = viewModel::acceptInvite,
            onRemoveInvite = viewModel::removeInvite,
            onMessageShown = viewModel::dismissMessage,
        ),
    )
}

/** Everything Home can do, bundled so the content stays easy to call from tests and previews. */
data class HomeActions(
    val onSignIn: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onStartQuickGame: (Pair<String, String>, MatchSettings) -> Unit = { _, _ -> },
    val onResumeQuickGame: (String) -> Unit = {},
    val onOpenGuestGame: (String) -> Unit = {},
    /** (game id, rivalry id, the guest id that was you) */
    val onSaveGuestGame: (String, String, String) -> Unit = { _, _, _ -> },
    val onOpenRivalry: (String) -> Unit = {},
    /** Opens the session already running against a rival. */
    val onResumeSession: (String) -> Unit = {},
    val onAddRival: () -> Unit = {},
    val onAcceptInvite: (String) -> Unit = {},
    val onRemoveInvite: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/**
 * Home is a scoreboard: the head to head with the rival you're playing, in white, with the form
 * bar and tonight's score under it and one primary action. Everything else — the other rivals,
 * Add a rival, Quick game, the games kept on this phone — is a plain row below it.
 */
@Composable
internal fun HomeContent(uiState: HomeUiState, actions: HomeActions) {
    var settingUpGame by rememberSaveable { mutableStateOf(false) }
    var savingGameId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
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
            HomeTopBar(uiState, actions)

            if (uiState.loading) {
                LoadingState("Looking for your rivals")
                return@Column
            }

            val hero = uiState.rivals.firstOrNull()
            if (hero != null) {
                Hero(hero, actions)
            } else if (uiState.signedIn) {
                NoRivalsYet(actions)
            } else {
                SignedOut(uiState, actions) { settingUpGame = true }
            }

            val incoming = uiState.invites.filter { it.incoming }
            val outgoing = uiState.invites.filter { !it.incoming }
            if (incoming.isNotEmpty() || uiState.rivals.size > 1 || outgoing.isNotEmpty()) {
                Column {
                    incoming.forEach { InviteRow(it, actions) }
                    uiState.rivals.drop(1).forEach { RivalRow(it, actions) }
                    outgoing.forEach { PendingRow(it, actions) }
                }
            }

            Column {
                // With no rivals yet, Add a rival is already the screen's primary action.
                if (uiState.signedIn && uiState.rivals.isNotEmpty()) {
                    ListRow(
                        title = "Add a rival",
                        onClick = actions.onAddRival,
                        leading = { RowIcon(R.drawable.ic_add) },
                        trailing = { RowChevron() },
                    )
                }
                QuickGameRow(uiState, actions, onSetUp = { settingUpGame = true })
            }

            if (uiState.finishedGuestGames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                    Label("On this phone")
                    uiState.finishedGuestGames.forEach { game ->
                        GuestGameRow(
                            game = game,
                            canSave = uiState.signedIn && uiState.rivals.isNotEmpty(),
                            onOpen = { actions.onOpenGuestGame(game.id) },
                            onSave = { savingGameId = game.id },
                        )
                    }
                    if (!uiState.signedIn) {
                        Text(
                            "Sign in and add a rival to save these to your head to head.",
                            style = Rivals.type.caption,
                            color = Rivals.colors.fg3,
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (settingUpGame) {
        QuickGameDialog(
            myName = uiState.myName,
            onStart = { names, settings ->
                settingUpGame = false
                actions.onStartQuickGame(names, settings)
            },
            onDismiss = { settingUpGame = false },
        )
    }
    val saving = uiState.finishedGuestGames.firstOrNull { it.id == savingGameId }
    if (saving != null) {
        SaveGameDialog(
            game = saving,
            myName = uiState.myName,
            rivals = uiState.rivals,
            onSave = { rivalryId, myGuestId ->
                savingGameId = null
                actions.onSaveGuestGame(saving.id, rivalryId, myGuestId)
            },
            onDismiss = { savingGameId = null },
        )
    }
}

@Composable
private fun HomeTopBar(uiState: HomeUiState, actions: HomeActions) {
    var menuOpen by remember { mutableStateOf(false) }
    TopBar("Rivals") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.pendingSync) {
                Icon(
                    painterResource(R.drawable.ic_cloud_upload),
                    contentDescription = "Waiting to sync",
                    tint = Rivals.colors.fg3,
                    modifier = Modifier.padding(horizontal = Space.s8).size(16.dp),
                )
            }
            if (uiState.signedIn) {
                Box {
                    IconAction(R.drawable.ic_more_vert, "More", { menuOpen = true })
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = Rivals.colors.surface,
                        shape = Shapes.panel,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sign out", style = Rivals.type.body, color = Rivals.colors.fg) },
                            onClick = { menuOpen = false; actions.onSignOut() },
                        )
                    }
                }
            } else {
                TextAction("Sign in", actions.onSignIn, color = Rivals.colors.fg)
            }
        }
    }
}

/** The rival you're playing, or the first of them: the screen's scoreboard. */
@Composable
private fun Hero(rival: RivalCard, actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("All time", modifier = Modifier.weight(1f))
            if (rival.live) LiveChip()
        }
        HeadToHead(
            yourScore = rival.myWins,
            rivalScore = rival.rivalWins,
            yourName = "You",
            rivalName = rival.name,
            numberStyle = Rivals.type.score.copy(fontSize = 96.sp, lineHeight = 96.sp),
        )
        if (rival.nights.isNotEmpty()) {
            FormBar(rival.nights, yourName = "You", rivalName = rival.name)
            Label("Last ${rival.nights.size} nights")
        }
    }
    val tonight = rival.tonight
    Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        if (tonight != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Label("Tonight", modifier = Modifier.weight(1f))
                Text(
                    "${tonight.first} – ${tonight.second}",
                    style = Rivals.type.number.copy(fontSize = 20.sp),
                    color = Rivals.colors.fg,
                )
            }
        }
        if (rival.activeSessionId != null) {
            PrimaryButton("Resume session", { actions.onResumeSession(rival.activeSessionId) })
        } else {
            PrimaryButton("Play ${rival.name}", { actions.onOpenRivalry(rival.rivalryId) })
        }
    }
}

/** Signed in, but nobody to play yet. */
@Composable
private fun NoRivalsYet(actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        Text("No rivals yet", style = Rivals.type.headline, color = Rivals.colors.fg)
        Text(
            "Invite the friend you play against. Once they accept, every session you play " +
                "together counts towards your head to head.",
            style = Rivals.type.body,
            color = Rivals.colors.fg2,
        )
        PrimaryButton("Add a rival", actions.onAddRival)
    }
}

/** Signed out: keeping score needs no account, and the pitch for signing in comes after it. */
@Composable
private fun SignedOut(uiState: HomeUiState, actions: HomeActions, onSetUpGame: () -> Unit) {
    val active = uiState.activeGuestGame
    Column(verticalArrangement = Arrangement.spacedBy(Space.s12)) {
        Text("Keep score", style = Rivals.type.headline, color = Rivals.colors.fg)
        Text(
            "Anyone can keep score here, no account needed; the game is saved on this phone.",
            style = Rivals.type.body,
            color = Rivals.colors.fg2,
        )
        if (active != null) {
            PrimaryButton("Resume quick game", { actions.onResumeQuickGame(active.id) })
            Text(
                "${active.left.name} ${active.left.wins} – ${active.right.wins} ${active.right.name}",
                style = Rivals.type.caption,
                color = Rivals.colors.fg3,
            )
        } else {
            PrimaryButton("Quick game", onSetUpGame)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
        Text("Got a rival?", style = Rivals.type.title, color = Rivals.colors.fg)
        Text(
            "Sign in and invite them. Every session you play together is kept on both your " +
                "phones, with a head to head, history and stats.",
            style = Rivals.type.body,
            color = Rivals.colors.fg2,
        )
        SecondaryButton("Sign in with Google", actions.onSignIn)
    }
}

/**
 * Quick game as a plain row. Signed out it is already the screen's primary action, so the row
 * would be the same offer twice.
 */
@Composable
private fun QuickGameRow(uiState: HomeUiState, actions: HomeActions, onSetUp: () -> Unit) {
    if (!uiState.signedIn) return
    val active = uiState.activeGuestGame
    if (active != null) {
        ListRow(
            title = "Resume quick game",
            subtitle = "${active.left.name} ${active.left.wins} – " +
                "${active.right.wins} ${active.right.name}",
            onClick = { actions.onResumeQuickGame(active.id) },
            leading = { RowIcon(R.drawable.ic_bolt) },
            trailing = { RowChevron() },
        )
    } else {
        ListRow(
            title = "Quick game",
            subtitle = if (uiState.signedIn) "Anyone, no account needed" else null,
            onClick = onSetUp,
            leading = { RowIcon(R.drawable.ic_bolt) },
            trailing = { RowChevron() },
        )
    }
}

@Composable
private fun InviteRow(invite: InviteCard, actions: HomeActions) {
    ListRow(
        title = "${invite.name} wants a rivalry",
        leading = { Avatar(invite.name) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                TextAction("Decline", { actions.onRemoveInvite(invite.rivalryId) })
                SecondaryButton("Accept", { actions.onAcceptInvite(invite.rivalryId) })
            }
        },
    )
}

@Composable
private fun RivalRow(rival: RivalCard, actions: HomeActions) {
    ListRow(
        title = rival.name,
        subtitle = if (rival.live) "Playing now" else null,
        onClick = { actions.onOpenRivalry(rival.rivalryId) },
        leading = { Avatar(rival.name) },
        trailing = {
            Text(
                "${rival.myWins} – ${rival.rivalWins}",
                style = Rivals.type.number,
                color = Rivals.colors.fg,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun PendingRow(invite: InviteCard, actions: HomeActions) {
    ListRow(
        title = invite.name,
        subtitle = "Waiting for them to accept",
        leading = { Avatar(invite.name) },
        trailing = { TextAction("Cancel", { actions.onRemoveInvite(invite.rivalryId) }) },
    )
}

@Composable
private fun GuestGameRow(game: GuestGame, canSave: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    ListRow(
        title = "${game.left.name} ${game.left.wins} – ${game.right.wins} ${game.right.name}",
        subtitle = game.startedAt?.let { "${formatDay(it)} · quick game" } ?: "Quick game",
        onClick = onOpen,
        trailing = { if (canSave) SecondaryButton("Save", onSave) },
    )
}

/** Names for the two sides, then the first match's settings. */
@Composable
private fun QuickGameDialog(
    myName: String,
    onStart: (Pair<String, String>, MatchSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaults = rememberMatchDefaults()
    var left by rememberSaveable { mutableStateOf(myName) }
    var right by rememberSaveable { mutableStateOf("") }
    var settings by remember { mutableStateOf(defaults.last) }
    RivalsDialog(
        title = "Quick game",
        confirmLabel = "Start",
        onConfirm = {
            defaults.last = settings
            onStart(left to right, settings)
        },
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.s16),
        ) {
            NameField(left, { left = it }, "Player 1", Rivals.colors.you)
            NameField(right, { right = it }, "Player 2", Rivals.colors.rival)
            MatchSettingsPicker(settings, { settings = it })
        }
    }
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    labelColor: Color = Rivals.colors.fg3,
) {
    RivalsTextField(
        value = value,
        onValueChange = { onValueChange(it.take(30)) },
        label = label,
        labelColor = labelColor,
        placeholder = label,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
    )
}

/** Which rivalry a guest game goes to, and which side of it was you. */
@Composable
private fun SaveGameDialog(
    game: GuestGame,
    myName: String,
    rivals: List<RivalCard>,
    onSave: (rivalryId: String, myGuestId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var rivalryId by rememberSaveable { mutableStateOf(rivals.singleOrNull()?.rivalryId) }
    val guess = listOf(game.left, game.right)
        .firstOrNull { it.name.equals(myName.substringBefore(' '), ignoreCase = true) }
    var myGuestId by rememberSaveable { mutableStateOf(guess?.id) }
    RivalsDialog(
        title = "Save to a rivalry",
        confirmLabel = "Save",
        onConfirm = {
            val r = rivalryId
            val g = myGuestId
            if (r != null && g != null) onSave(r, g)
        },
        confirmEnabled = rivalryId != null && myGuestId != null,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s16)) {
            ChoiceRow(
                label = "Which one was you?",
                options = listOf(game.left, game.right).map { it.id to it.name },
                selected = myGuestId,
                onSelect = { myGuestId = it },
            )
            ChoiceRow(
                label = "Against",
                options = rivals.map { it.rivalryId to it.name },
                selected = rivalryId,
                onSelect = { rivalryId = it },
            )
        }
    }
}

@Preview
@Composable
private fun HomeContentPreview() {
    RivalsTheme {
        HomeContent(
            HomeUiState(
                loading = false,
                signedIn = true,
                myName = "Kevin",
                rivals = listOf(
                    RivalCard(
                        "r", "Julian", 12, 9,
                        activeSessionId = "s",
                        tonight = 2 to 1,
                        nights = listOf(true, true, false, true, true, false, false, true, true, false),
                    ),
                ),
                invites = listOf(InviteCard("i", "Sam", incoming = true)),
                guestGames = listOf(
                    GuestGame("g", false, null, GuestSide("a", "Kevin", 2), GuestSide("b", "Tom", 1)),
                ),
            ),
            HomeActions(),
        )
    }
}
