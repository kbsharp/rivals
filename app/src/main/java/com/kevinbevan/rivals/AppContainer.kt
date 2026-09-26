package com.kevinbevan.rivals

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.FirestoreSessionStore
import com.kevinbevan.rivals.data.GuestRepository
import com.kevinbevan.rivals.data.LocalSessionStore
import com.kevinbevan.rivals.data.MatchDefaults
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.RivalryRepository
import com.kevinbevan.rivals.data.SeenResults
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.ScoreRules
import java.io.File

/**
 * Manual dependency injection. Repositories are created here and handed to ViewModels
 * through their factories.
 */
class AppContainer(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val rules = ScoreRules(newId = { firestore.collection("_").document().id })

    val authRepository = AuthRepository(
        auth = FirebaseAuth.getInstance(),
        credentialManager = CredentialManager.create(context),
        serverClientId = context.getString(R.string.default_web_client_id),
    )

    val playerRepository = PlayerRepository(firestore)

    /** How the last match was set up, so the next one opens the same way. */
    val matchDefaults = MatchDefaults(context)

    /** Which match-won panel each session last showed, so resuming doesn't show it again. */
    val seenResults = SeenResults(context)

    private val cloudStore = FirestoreSessionStore(firestore, playerRepository)
    private val guestStore = LocalSessionStore(File(context.filesDir, "guest-games.json"))

    val rivalryRepository = RivalryRepository(firestore, cloudStore, rules)

    /** Rivals' sessions, in Firestore. */
    val sessionRepository = SessionRepository(cloudStore, rules)

    /** Guest games, on the phone. Played through the same [SessionRepository] logic. */
    val guestSessionRepository = SessionRepository(guestStore, rules)
    val guestRepository = GuestRepository(guestStore, rules)

    fun sessions(guest: Boolean): SessionRepository = if (guest) guestSessionRepository else sessionRepository
}
