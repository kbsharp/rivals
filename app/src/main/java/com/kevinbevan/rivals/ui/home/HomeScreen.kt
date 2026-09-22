package com.kevinbevan.rivals.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.history.formatDay
import com.kevinbevan.rivals.ui.navigation.SessionRoute
import com.kevinbevan.rivals.ui.session.DefaultMatchSettings
import com.kevinbevan.rivals.ui.session.MatchSettingsPicker
import com.kevinbevan.rivals.ui.theme.RivalsTheme

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
    val onAddRival: () -> Unit = {},
    val onAcceptInvite: (String) -> Unit = {},
    val onRemoveInvite: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(uiState: HomeUiState, actions: HomeActions) {
    var settingUpGame by rememberSaveable { mutableStateOf(false) }
    var savingGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            actions.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rivals") },
                actions = {
                    if (uiState.pendingSync) {
                        Icon(
                            painterResource(R.drawable.ic_cloud_upload),
                            contentDescription = "Waiting to sync",
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    if (uiState.signedIn) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = {
                                    menuOpen = false
                                    actions.onSignOut()
                                },
                            )
                        }
                    } else {
                        TextButton(onClick = actions.onSignIn) { Text("Sign in") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            val active = uiState.activeGuestGame
            if (active != null) {
                Button(
                    onClick = { actions.onResumeQuickGame(active.id) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Resume quick game", style = MaterialTheme.typography.titleMedium)
                        Text("${active.left.name} ${active.left.wins} – ${active.right.wins} ${active.right.name}")
                    }
                }
            } else {
                Button(
                    onClick = { settingUpGame = true },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) { Text("Quick game", style = MaterialTheme.typography.titleMedium) }
            }
            Text(
                "Keep score for any game, no account needed. It's saved on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.signedIn) {
                RivalsSection(uiState, actions)
            } else {
                SignInPitch(actions.onSignIn)
            }

            if (uiState.finishedGuestGames.isNotEmpty()) {
                SectionTitle("Games on this phone")
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
private fun RivalsSection(uiState: HomeUiState, actions: HomeActions) {
    SectionTitle("Your rivals")
    uiState.invites.filter { it.incoming }.forEach { invite ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${invite.name} wants a rivalry with you", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.onAcceptInvite(invite.rivalryId) }) { Text("Accept") }
                    TextButton(onClick = { actions.onRemoveInvite(invite.rivalryId) }) { Text("Decline") }
                }
            }
        }
    }
    uiState.rivals.forEach { rival ->
        OutlinedCard(
            onClick = { actions.onOpenRivalry(rival.rivalryId) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rival.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (rival.live) {
                        Text("Session running", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("${rival.myWins} – ${rival.rivalWins}", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
    uiState.invites.filter { !it.incoming }.forEach { invite ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Waiting for ${invite.name} to accept",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { actions.onRemoveInvite(invite.rivalryId) }) { Text("Cancel") }
            }
        }
    }
    if (uiState.rivals.isEmpty() && uiState.invites.isEmpty()) {
        Text(
            "Invite the friend you play against. Once they accept, every session you play together counts.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    OutlinedButton(onClick = actions.onAddRival, modifier = Modifier.fillMaxWidth()) { Text("Add a rival") }
}

@Composable
private fun SignInPitch(onSignIn: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Got a rival?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sign in and invite them. Every session you play together is kept, on both your phones, with a head to head, history and stats.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSignIn) { Text("Sign in with Google") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun GuestGameRow(game: GuestGame, canSave: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${game.left.name} ${game.left.wins} – ${game.right.wins} ${game.right.name}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            game.startedAt?.let {
                Text(formatDay(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canSave) TextButton(onClick = onSave) { Text("Save") }
    }
}

/** Names for the two sides, then the first match's settings. */
@Composable
private fun QuickGameDialog(
    myName: String,
    onStart: (Pair<String, String>, MatchSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var left by rememberSaveable { mutableStateOf(myName) }
    var right by rememberSaveable { mutableStateOf("") }
    var settings by remember { mutableStateOf(DefaultMatchSettings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick game") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NameField(left, { left = it }, "Player 1")
                NameField(right, { right = it }, "Player 2")
                MatchSettingsPicker(settings, { settings = it })
            }
        },
        confirmButton = { TextButton(onClick = { onStart(left to right, settings) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(30)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
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
    val guess = listOf(game.left, game.right).firstOrNull { it.name.equals(myName.substringBefore(' '), ignoreCase = true) }
    var myGuestId by rememberSaveable { mutableStateOf(guess?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to a rivalry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Which one was you?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(game.left, game.right).forEach { side ->
                        FilterChip(selected = myGuestId == side.id, onClick = { myGuestId = side.id }, label = { Text(side.name) })
                    }
                }
                Text("Against", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rivals.forEach { rival ->
                        FilterChip(
                            selected = rivalryId == rival.rivalryId,
                            onClick = { rivalryId = rival.rivalryId },
                            label = { Text(rival.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            val r = rivalryId
            val g = myGuestId
            TextButton(onClick = { if (r != null && g != null) onSave(r, g) }, enabled = r != null && g != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
                rivals = listOf(RivalCard("r", "Julian", 12, 9, live = true)),
                invites = listOf(InviteCard("i", "Sam", incoming = true)),
                guestGames = listOf(GuestGame("g", false, null, GuestSide("a", "Kevin", 2), GuestSide("b", "Tom", 1))),
            ),
            HomeActions(),
        )
    }
}
