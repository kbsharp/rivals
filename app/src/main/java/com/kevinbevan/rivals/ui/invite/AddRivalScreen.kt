package com.kevinbevan.rivals.ui.invite

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.model.capitalised
import com.kevinbevan.rivals.R
import com.kevinbevan.rivals.ui.components.Avatar
import com.kevinbevan.rivals.ui.components.ListRow
import com.kevinbevan.rivals.ui.components.LoadingState
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.RivalsTextField
import com.kevinbevan.rivals.ui.components.RowChevron
import com.kevinbevan.rivals.ui.components.RowIcon
import com.kevinbevan.rivals.ui.components.SecondaryButton
import com.kevinbevan.rivals.ui.components.TopBar
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Space

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

/** The three ways to reach a rival. One is open at a time, so there's one action on screen. */
private enum class AddRivalWay { EMAIL, LINK, CODE }

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
    // The search opens its own row, so a result is never hidden behind a collapsed one.
    var open by rememberSaveable {
        mutableStateOf(if (uiState.searched != null) AddRivalWay.EMAIL else null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
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
            verticalArrangement = Arrangement.spacedBy(Space.s16),
        ) {
            TopBar("Add a rival", onBack = onBack)

            Way(
                way = AddRivalWay.EMAIL,
                open = open,
                onToggle = { open = it },
                iconRes = R.drawable.ic_mail,
                title = "Find by email",
                subtitle = "They're already on Rivals",
            ) {
                RivalsTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Their email",
                    placeholder = "name@example.com",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Search,
                    ),
                    onImeAction = { onSearch(email) },
                )
                PrimaryButton(
                    "Find",
                    { onSearch(email) },
                    enabled = !uiState.busy && email.isNotBlank(),
                )
                Found(uiState, onInvite)
            }

            Way(
                way = AddRivalWay.LINK,
                open = open,
                onToggle = { open = it },
                iconRes = R.drawable.ic_share,
                title = "Share a link",
                subtitle = "For someone who isn't on Rivals yet",
            ) {
                Text(
                    "When they open the link and sign in, you're rivals straight away. " +
                        "Each link works once, and lasts 30 days.",
                    style = Rivals.type.body,
                    color = Rivals.colors.fg2,
                )
                PrimaryButton("Share an invite link", onShareLink, enabled = !uiState.busy)
            }

            Way(
                way = AddRivalWay.CODE,
                open = open,
                onToggle = { open = it },
                iconRes = R.drawable.ic_hash,
                title = "Enter a code",
                subtitle = "Your rival sent you one",
            ) {
                RivalsTextField(
                    value = code,
                    onValueChange = { code = it.take(12) },
                    label = "Invite code",
                    placeholder = "ABCD2345",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Go,
                    ),
                    onImeAction = { if (code.isNotBlank()) onOpenInvite(code.trim()) },
                )
                PrimaryButton(
                    "Open invite",
                    { onOpenInvite(code.trim()) },
                    enabled = code.isNotBlank(),
                )
            }

            if (uiState.busy) LoadingState("Working on it")
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** One of the three rows: a plain row until it's tapped, then its field and its action. */
@Composable
private fun Way(
    way: AddRivalWay,
    open: AddRivalWay?,
    onToggle: (AddRivalWay?) -> Unit,
    iconRes: Int,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val isOpen = open == way
    Column {
        ListRow(
            title = title,
            subtitle = subtitle,
            onClick = { onToggle(if (isOpen) null else way) },
            leading = { RowIcon(iconRes) },
            trailing = { if (!isOpen) RowChevron() },
        )
        AnimatedVisibility(isOpen) {
            Column(
                modifier = Modifier.padding(bottom = Space.s8),
                verticalArrangement = Arrangement.spacedBy(Space.s12),
            ) {
                content()
            }
        }
    }
}

/** Who the address belongs to, once it's been looked up. */
@Composable
private fun Found(uiState: AddRivalUiState, onInvite: () -> Unit) {
    val found = uiState.found
    when {
        found != null && uiState.invited -> ListRow(
            title = capitalised(found.displayName).ifBlank { found.shortName },
            subtitle = "Invited. Once they accept, you can start sessions together.",
            leading = { Avatar(found.shortName) },
        )
        found != null -> ListRow(
            title = capitalised(found.displayName).ifBlank { found.shortName },
            subtitle = found.email,
            leading = { Avatar(found.shortName) },
            trailing = {
                SecondaryButton("Invite", onInvite, enabled = !uiState.busy)
            },
        )
        uiState.searched != null -> Text(
            "Nobody on Rivals signs in with ${uiState.searched}. Share a link instead.",
            style = Rivals.type.body,
            color = Rivals.colors.fg2,
        )
    }
}

@Preview
@Composable
private fun AddRivalPreview() {
    RivalsTheme { AddRivalContent(AddRivalUiState(), {}, {}, {}, {}, {}, {}) }
}
