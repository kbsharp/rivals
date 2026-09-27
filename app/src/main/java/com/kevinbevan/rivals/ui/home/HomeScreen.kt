package com.kevinbevan.rivals.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.components.Avatar
import com.kevinbevan.rivals.ui.components.ChoiceRow
import com.kevinbevan.rivals.ui.components.EmptyFormBar
import com.kevinbevan.rivals.ui.components.HeadToHead
import com.kevinbevan.rivals.ui.components.IconAction
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.ListRow
import com.kevinbevan.rivals.ui.components.LoadingState
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.RackMark
import com.kevinbevan.rivals.ui.components.RivalsTextField
import com.kevinbevan.rivals.ui.components.SplitBar
import com.kevinbevan.rivals.ui.components.SecondaryButton
import com.kevinbevan.rivals.ui.components.TextAction
import com.kevinbevan.rivals.ui.components.TopBar
import com.kevinbevan.rivals.ui.history.formatDay
import com.kevinbevan.rivals.ui.navigation.SessionRoute
import com.kevinbevan.rivals.ui.rememberMatchDefaults
import com.kevinbevan.rivals.ui.session.MatchSettingsPicker
import com.kevinbevan.rivals.ui.session.RivalsDialog
import com.kevinbevan.rivals.ui.theme.Montserrat
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
    /** Opens a finished quick game's detail, and whether it could be saved from there. */
    onOpenGuestGame: (id: String, canSave: Boolean) -> Unit,
    /** A quick game to open the Save dialog for, asked for by its detail. */
    saveRequest: String? = null,
    onSaveRequestHandled: () -> Unit = {},
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
            onOpenGuestGame = { onOpenGuestGame(it, uiState.canSaveGuestGames) },
            onSaveGuestGame = viewModel::saveGuestGame,
            onOpenRivalry = onOpenRivalry,
            onAddRival = onAddRival,
            onAcceptInvite = viewModel::acceptInvite,
            onRemoveInvite = viewModel::removeInvite,
            onMessageShown = viewModel::dismissMessage,
            onSaveRequestHandled = onSaveRequestHandled,
        ),
        saveRequest = saveRequest,
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
    /** A rival's row opens their rivalry, where a night is started or resumed. */
    val onOpenRivalry: (String) -> Unit = {},
    val onAddRival: () -> Unit = {},
    val onAcceptInvite: (String) -> Unit = {},
    val onRemoveInvite: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onSaveRequestHandled: () -> Unit = {},
)

/**
 * Home is a scoreboard of every rival: one mirrored table, you on the left and them on the right,
 * the rivals you play most recently first. A rival's row is the way into a night with them.
 * Below it, labelled blocks — Play and On this phone — kept apart by space, not lines.
 */
@Composable
internal fun HomeContent(uiState: HomeUiState, actions: HomeActions, saveRequest: String? = null) {
    var settingUpGame by rememberSaveable { mutableStateOf(false) }
    var savingGameId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(saveRequest) {
        if (saveRequest != null) {
            savingGameId = saveRequest
            actions.onSaveRequestHandled()
        }
    }
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
            verticalArrangement = Arrangement.spacedBy(Space.s32),
        ) {
            HomeTopBar(uiState, actions)

            if (uiState.loading) {
                LoadingState("Looking for your rivals")
                return@Column
            }

            if (uiState.signedIn && uiState.invites.isNotEmpty()) {
                Section("Invites") {
                    uiState.invites.filter { it.incoming }.forEach { InviteRow(it, actions) }
                    uiState.invites.filter { !it.incoming }.forEach { PendingRow(it, actions) }
                }
            }

            if (uiState.rivals.isNotEmpty()) {
                RivalsBoard(uiState.rivals, actions)
            } else if (uiState.signedIn) {
                NoRivalsYet(actions)
            } else {
                SignedOut(uiState, actions) { settingUpGame = true }
            }

            // Signed out, Quick game is already the screen's primary action; with no rivals yet,
            // so is Add a rival.
            if (uiState.signedIn && uiState.rivals.isNotEmpty()) {
                Section("Play") { PlayTiles(uiState, actions, onSetUp = { settingUpGame = true }) }
            }

            if (uiState.finishedGuestGames.isNotEmpty()) {
                GuestGames(uiState, actions, onSave = { savingGameId = it })
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
    TopBar("Rivals", leading = { RackMark() }) {
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

/** How many rivals show before See all. */
private const val RIVALS_SHOWN = 3

/**
 * Every rival as one mirrored table in a panel: your wins, their name, theirs, and the record
 * as a split bar. Only the first few show until See all.
 */
@Composable
private fun RivalsBoard(rivals: List<RivalCard>, actions: HomeActions) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s16)) {
            Label("You", color = Rivals.colors.you, modifier = Modifier.weight(1f))
            Label("Rivals", textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Label("Them", color = Rivals.colors.rival, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Rivals.colors.surface, Shapes.panel)
                .padding(vertical = Space.s8),
        ) {
            (if (showAll) rivals else rivals.take(RIVALS_SHOWN)).forEach { rival ->
                RivalScoreRow(rival, onClick = { actions.onOpenRivalry(rival.rivalryId) })
            }
            if (rivals.size > RIVALS_SHOWN) {
                Box(Modifier.fillMaxWidth().padding(top = Space.s4), contentAlignment = Alignment.Center) {
                    TextAction(if (showAll) "Show fewer" else "See all ${rivals.size} rivals", { showAll = !showAll })
                }
            }
        }
    }
}

/**
 * One rival: your all-time wins on the left, theirs on the right, the leader's white and heavy
 * and the other `fg-3` as in the Stats table, their name between them and who leads under it.
 */
@Composable
private fun RivalScoreRow(rival: RivalCard, onClick: () -> Unit) {
    val lead = rival.myWins - rival.rivalWins
    val tonight = rival.tonight
    val (subtitle, subtitleColor) = when {
        rival.live && tonight != null -> "Playing now · ${tonight.first} – ${tonight.second}" to Rivals.colors.live
        rival.live -> "Playing now" to Rivals.colors.live
        lead > 0 -> "You lead by $lead" to Rivals.colors.you
        lead < 0 -> "${rival.name} leads by ${-lead}" to Rivals.colors.rival
        else -> "Tied" to Rivals.colors.fg3
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s16, vertical = Space.s12),
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RecordNumber(rival.myWins, leading = lead >= 0, align = TextAlign.Start)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(rival.name, style = Rivals.type.rowTitle, color = Rivals.colors.fg, maxLines = 1)
                Text(subtitle, style = Rivals.type.caption, color = subtitleColor, maxLines = 1)
            }
            RecordNumber(rival.rivalWins, leading = lead <= 0, align = TextAlign.End)
        }
        SplitBar(rival.myWins, rival.rivalWins)
    }
}

@Composable
private fun RecordNumber(value: Int, leading: Boolean, align: TextAlign) {
    Text(
        value.toString(),
        style = Rivals.type.headline.copy(
            fontWeight = if (leading) FontWeight.ExtraBold else FontWeight.SemiBold,
        ),
        color = if (leading) Rivals.colors.fg else Rivals.colors.fg3,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(Space.s48 + Space.s24),
    )
}

/** The screen's no-rivals scoreboard: bigger than `display`, because it is the screen's hero. */
@Composable
@ReadOnlyComposable
private fun heroScore() = Rivals.type.score.copy(fontSize = 96.sp, lineHeight = 96.sp)

/** Signed in, but nobody to play yet: the scoreboard waiting at 0 – 0 for a rival. */
@Composable
private fun NoRivalsYet(actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s24)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("Head to head")
            HeadToHead(
                yourScore = 0,
                rivalScore = 0,
                yourName = "You",
                rivalName = "Your rival",
                numberStyle = heroScore(),
                spread = true,
            )
            EmptyFormBar()
        }
        Text(
            "Invite who you play. Every game counts.",
            style = Rivals.type.body,
            color = Rivals.colors.fg2,
        )
        PrimaryButton("Add a rival", actions.onAddRival)
    }
}

/** One labelled block of Home: the label (with an optional action beside it), then its rows. */
@Composable
private fun Section(
    label: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Space.s24),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(label, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

/** How many quick games show before See all. */
private const val GUEST_GAMES_SHOWN = 3

/** Quick games kept on this phone, newest first, in one panel. */
@Composable
private fun GuestGames(uiState: HomeUiState, actions: HomeActions, onSave: (String) -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val games = uiState.finishedGuestGames
    val canSave = uiState.canSaveGuestGames
    Section(
        "On this phone",
        action = if (games.size > GUEST_GAMES_SHOWN) {
            { TextAction(if (showAll) "Show fewer" else "See all ${games.size}", { showAll = !showAll }) }
        } else {
            null
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Rivals.colors.surface, Shapes.panel)
                .padding(horizontal = Space.s16, vertical = Space.s4),
        ) {
            (if (showAll) games else games.take(GUEST_GAMES_SHOWN)).forEach { game ->
                GuestGameRow(
                    game = game,
                    canSave = canSave,
                    onOpen = { actions.onOpenGuestGame(game.id) },
                    onSave = { onSave(game.id) },
                )
            }
        }
        if (!uiState.signedIn) {
            Text(
                "Sign in and add a rival to save these to your head to head.",
                style = Rivals.type.caption,
                color = Rivals.colors.fg3,
                modifier = Modifier.padding(top = Space.s8),
            )
        }
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
 * Play as equal tiles: the quick game (or the one running, to resume; there's only ever one on
 * the phone) and Add a rival.
 */
@Composable
private fun PlayTiles(uiState: HomeUiState, actions: HomeActions, onSetUp: () -> Unit) {
    val active = uiState.activeGuestGame
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s12)) {
        if (active != null) {
            PlayTile(
                R.drawable.ic_bolt,
                "Resume",
                "${active.left.name} ${active.left.wins} – ${active.right.wins} ${active.right.name}",
                { actions.onResumeQuickGame(active.id) },
                Modifier.weight(1f),
            )
        } else {
            PlayTile(R.drawable.ic_bolt, "Quick game", "No rival needed", onSetUp, Modifier.weight(1f))
        }
        PlayTile(R.drawable.ic_add, "Add a rival", "Email, link or code", actions.onAddRival, Modifier.weight(1f))
    }
}

@Composable
private fun PlayTile(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .heightIn(min = Space.s48 * 2)
            .clip(Shapes.panel)
            .background(Rivals.colors.surface)
            .clickable(onClick = onClick)
            .padding(Space.s16),
        verticalArrangement = Arrangement.spacedBy(Space.s12),
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = Rivals.colors.fg)
        Column {
            Text(title, style = Rivals.type.rowTitle, color = Rivals.colors.fg, maxLines = 1)
            Text(subtitle, style = Rivals.type.caption, color = Rivals.colors.fg3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InviteRow(invite: InviteCard, actions: HomeActions) {
    ListRow(
        title = "${invite.name} wants a rivalry",
        leading = { Avatar(invite.name, background = Rivals.colors.rivalTint, color = Rivals.colors.rival) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s8)) {
                TextAction("Decline", { actions.onRemoveInvite(invite.rivalryId) })
                SecondaryButton("Accept", { actions.onAcceptInvite(invite.rivalryId) })
            }
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

/**
 * A finished quick game: Player 1 in `you`, Player 2 in `rival` (as the Quick game dialog
 * labels them), the score in white, and the winner named in their colour.
 */
@Composable
private fun GuestGameRow(game: GuestGame, canSave: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    val colors = Rivals.colors
    val title = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.you)) { append(game.left.name) }
        append(" ")
        withStyle(SpanStyle(fontFamily = Montserrat, fontWeight = FontWeight.ExtraBold)) {
            append("${game.left.wins} – ${game.right.wins}")
        }
        append(" ")
        withStyle(SpanStyle(color = colors.rival)) { append(game.right.name) }
    }
    val subtitle = buildAnnotatedString {
        game.startedAt?.let { append("${formatDay(it)} · ") }
        when {
            game.left.wins > game.right.wins ->
                withStyle(SpanStyle(color = colors.you)) { append("${game.left.name} won") }
            game.right.wins > game.left.wins ->
                withStyle(SpanStyle(color = colors.rival)) { append("${game.right.name} won") }
            else -> append("Tied")
        }
    }
    ListRow(
        title = title,
        subtitle = subtitle,
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
