package com.kevinbevan.rivals

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository

/**
 * Manual dependency injection. Repositories are created here and handed to ViewModels
 * through their factories.
 */
class AppContainer(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    val authRepository = AuthRepository(
        auth = FirebaseAuth.getInstance(),
        credentialManager = CredentialManager.create(context),
        serverClientId = context.getString(R.string.default_web_client_id),
    )

    val playerRepository = PlayerRepository(firestore)
}
