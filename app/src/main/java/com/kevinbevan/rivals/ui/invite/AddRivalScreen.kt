package com.kevinbevan.rivals.ui.invite

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.R

@Composable
fun AddRivalScreen(
    onOpenInvite: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AddRivalViewModel = viewModel(factory = AddRivalViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(uiState.shareText) {
        uiState.shareText?.let { text ->
            viewModel.onShared()
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, "Invite your rival"))
        }
    }
    AddRivalContent(
        uiState = uiState,
        onSearch = viewModel::search,
        onInvite = viewModel::invite,
        onShareLink = viewModel::shareLink,
        onOpenInvite = onOpenInvite,
        onMessageShown = viewModel::dismissMessage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddRivalContent(
    uiState: AddRivalUiState,
    onSearch: (String) -> Unit,
    onInvite: () -> Unit,
    onShareLink: () -> Unit,
    onOpenInvite: (String) -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a rival") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text("Already on Rivals?", style = MaterialTheme.typography.titleMedium)
            Text("Enter the email address they sign in with.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Their email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(email) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSearch(email) }, enabled = !uiState.busy && email.isNotBlank()) { Text("Find") }

            val found = uiState.found
            when {
                found != null -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(found.displayName.ifBlank { found.shortName }, style = MaterialTheme.typography.titleMedium)
                        if (uiState.invited) {
                            Text("Invited. Once they accept, you can start sessions together.")
                        } else {
                            Button(onClick = onInvite, enabled = !uiState.busy) { Text("Invite ${found.shortName}") }
                        }
                    }
                }
                uiState.searched != null -> Text(
                    "Nobody on Rivals signs in with ${uiState.searched}. Send them a link instead.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Not on Rivals yet?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Send them a link. When they open it and sign in, you're rivals straight away. Each link works once.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onShareLink, enabled = !uiState.busy) { Text("Share an invite link") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Got an invite code?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(12) },
                label = { Text("Invite code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (code.isNotBlank()) onOpenInvite(code.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onOpenInvite(code.trim()) }, enabled = code.isNotBlank()) { Text("Open invite") }
        }
    }
}
