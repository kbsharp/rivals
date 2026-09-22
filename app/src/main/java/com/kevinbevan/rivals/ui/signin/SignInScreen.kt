package com.kevinbevan.rivals.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kevinbevan.rivals.ui.components.HeadToHead
import com.kevinbevan.rivals.ui.components.Label
import com.kevinbevan.rivals.ui.components.LoadingState
import com.kevinbevan.rivals.ui.components.PrimaryButton
import com.kevinbevan.rivals.ui.components.TopBar
import com.kevinbevan.rivals.ui.theme.Rivals
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import com.kevinbevan.rivals.ui.theme.Space

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: SignInViewModel = viewModel(factory = SignInViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.signedIn) {
        if (uiState.signedIn) {
            viewModel.onSignInHandled()
            onSignedIn()
        }
    }

    SignInContent(
        uiState = uiState,
        onSignInClick = { viewModel.signIn(context) },
        onErrorShown = viewModel::dismissError,
        onBack = onBack,
    )
}

/**
 * Signing in, as a scoreboard waiting to be played: an empty head to head, what it's for, and
 * the one button.
 */
@Composable
internal fun SignInContent(
    uiState: SignInUiState,
    onSignInClick: () -> Unit,
    onErrorShown: () -> Unit,
    onBack: () -> Unit,
) {
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
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.s24),
            verticalArrangement = Arrangement.spacedBy(Space.section),
        ) {
            TopBar("Rivals", onBack = onBack)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.section, Alignment.CenterVertically),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("All time")
                    HeadToHead(
                        yourScore = 0,
                        rivalScore = 0,
                        yourName = "You",
                        rivalName = "Your rival",
                    )
                }
                Text(
                    "Sign in to keep score with a rival. Every game you play together counts " +
                        "towards your head to head, on both your phones.",
                    style = Rivals.type.body,
                    color = Rivals.colors.fg2,
                )
            }
            if (uiState.inProgress) {
                LoadingState("Signing in")
            } else {
                PrimaryButton("Sign in with Google", onSignInClick)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Preview
@Composable
private fun SignInContentPreview() {
    RivalsTheme { SignInContent(SignInUiState(), onSignInClick = {}, onErrorShown = {}, onBack = {}) }
}
