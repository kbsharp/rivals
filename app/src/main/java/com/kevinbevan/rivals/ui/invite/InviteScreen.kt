package com.kevinbevan.rivals.ui.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R

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
    InviteContent(uiState, onSignIn = onSignIn, onAccept = viewModel::accept, onRetry = viewModel::load, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InviteContent(
    uiState: InviteUiState,
    onSignIn: () -> Unit,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val invite = uiState.invite
            when {
                uiState.loading || uiState.accepting -> CircularProgressIndicator()
                uiState.error != null -> {
                    Text(uiState.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
                invite == null -> Text(
                    "This invite has already been used, or the code is wrong. Ask your rival for a new link.",
                    textAlign = TextAlign.Center,
                )
                uiState.own -> Text(
                    "This is your own invite. Send the link to your rival; it'll work when they open it.",
                    textAlign = TextAlign.Center,
                )
                else -> {
                    Text(
                        "${invite.fromName.ifBlank { "Someone" }} wants a rivalry with you",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Every session you play together will count towards your head to head.",
                        textAlign = TextAlign.Center,
                    )
                    if (uiState.signedIn) {
                        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Accept") }
                    } else {
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text("Sign in with Google to accept")
                        }
                    }
                }
            }
        }
    }
}
