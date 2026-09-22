package com.kevinbevan.rivals.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.kevinbevan.rivals.model.Player
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in via Credential Manager, exchanged for a Firebase Auth session.
 *
 * @param serverClientId the OAuth *web* client id (`default_web_client_id`, generated from
 *   google-services.json). Google issues the ID token for this audience so Firebase can verify it.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val serverClientId: String,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    private var signOutReason: String? = null

    /** Why the last sign-out happened, if it wasn't the user's choice. Cleared once read. */
    fun takeSignOutReason(): String? = signOutReason.also { signOutReason = null }

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Shows the Google account picker and signs in to Firebase.
     *
     * @param activityContext must be an Activity context: Credential Manager draws its UI on it.
     * @throws androidx.credentials.exceptions.GetCredentialException if the picker fails or is cancelled.
     */
    suspend fun signInWithGoogle(activityContext: Context): Player {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        val credential = credentialManager.getCredential(activityContext, request).credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Unexpected credential type: ${credential.type}" }

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            ?: error("Firebase returned no user")
        return user.toPlayer()
    }

    /**
     * Signs out. A [reason] is kept for the sign-in screen to show, e.g. when Firestore starts
     * refusing this account.
     */
    suspend fun signOut(reason: String? = null) {
        signOutReason = reason
        auth.signOut()
        // Forget the chosen account so the picker shows again next time. Best effort: it throws
        // when no credential provider is available (e.g. outdated Play services), and signing
        // out of Firebase above is what matters.
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
        }
    }
}

fun FirebaseUser.toPlayer() = Player(
    uid = uid,
    displayName = displayName.orEmpty(),
    email = email.orEmpty(),
    photoUrl = photoUrl?.toString(),
)
