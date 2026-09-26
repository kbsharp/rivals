package com.kevinbevan.rivals.ui.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.model.capitalised
import com.kevinbevan.rivals.model.Invite
import com.kevinbevan.rivals.ui.components.EmptyState
import com.kevinbevan.rivals.ui.components.ErrorState
import com.kevinbevan.rivals.ui.components.HeadToHead
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.LoadingState
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.TopBar
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Space

@Composable
fun InviteScreen(
    onSignIn: () -> Unit,
    onAccepted: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: InviteViewModel = viewModel(factory = InviteViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.acceptedRivalryId) {
        uiState.acceptedRivalryId?.let {
            viewModel.onAcceptedHandled()
            onAccepted(it)
        }
    }
    InviteContent(
        uiState,
        onSignIn = onSignIn,
        onAccept = viewModel::accept,
        onRetry = viewModel::load,
        onBack = onBack,
    )
}

/**
 * An invite, drawn as the scoreboard it would start: their name against yours, nothing played
 * yet, and the one button that starts it.
 */
@Composable
internal fun InviteContent(
    uiState: InviteUiState,
    onSignIn: () -> Unit,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Rivals.colors.base)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.gutter)
            .padding(bottom = Space.s24),
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        TopBar("Invite", onBack = onBack)

        val invite = uiState.invite
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.section, Alignment.CenterVertically),
        ) {
            when {
                uiState.loading -> LoadingState("Opening the invite")
                uiState.accepting -> LoadingState("Setting up the rivalry")
                uiState.error != null -> ErrorState(uiState.error, onRetry = onRetry)
                invite == null -> EmptyState(
                    title = "This invite has gone",
                    body = "It's already been used, or the code is wrong. Ask your rival for a " +
                        "new link.",
                )
                uiState.own -> EmptyState(
                    title = "This is your own invite",
                    body = "Send the link to your rival; it'll work when they open it.",
                )
                else -> Scoreboard(invite)
            }
        }
        if (invite != null && !uiState.own && !uiState.loading && uiState.error == null) {
            if (uiState.signedIn) {
                PrimaryButton("Accept", onAccept, enabled = !uiState.accepting)
            } else {
                PrimaryButton("Sign in with Google to accept", onSignIn)
            }
        }
    }
}

@Composable
private fun Scoreboard(invite: Invite) {
    val from = capitalised(invite.fromName).ifBlank { "Someone" }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s24)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("All time")
            HeadToHead(yourScore = 0, rivalScore = 0, yourName = "You", rivalName = from)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.s8)) {
            Text(
                "$from wants a rivalry",
                style = Rivals.type.headline,
                color = Rivals.colors.fg,
            )
            Text(
                "Every session you play together will count towards your head to head, on " +
                    "both your phones.",
                style = Rivals.type.body,
                color = Rivals.colors.fg2,
            )
        }
    }
}

@Preview
@Composable
private fun InviteContentPreview() {
    RivalsTheme {
        InviteContent(
            InviteUiState(loading = false, invite = Invite("ABCD2345", "a", "Kevin"), signedIn = true),
            {}, {}, {}, {},
        )
    }
}
