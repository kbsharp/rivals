package com.poolscore.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.poolscore.ui.theme.PoolScoreTheme

@Composable
fun SignInScreen(onSignedIn: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Pool Score", style = MaterialTheme.typography.displayMedium)
            // TODO(milestone 2): Credential Manager Google sign-in → Firebase Auth.
            Button(onClick = onSignedIn) { Text("Sign in with Google") }
        }
    }
}

@Preview
@Composable
private fun SignInScreenPreview() {
    PoolScoreTheme { SignInScreen(onSignedIn = {}) }
}
