package com.kevinbevan.rivals.emulator

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.ScoreRules
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.Assert.fail

/** The local Firebase emulators, as seen from the Android emulator (the host is 10.0.2.2). */
object Emulators {
    private const val HOST = "10.0.2.2"
    private const val AUTH_PORT = 9099
    private const val FIRESTORE_PORT = 8080

    val projectId: String get() = FirebaseApp.getInstance().options.projectId!!

    fun useFor(auth: FirebaseAuth, db: FirebaseFirestore) {
        auth.useEmulator(HOST, AUTH_PORT)
        db.useEmulator(HOST, FIRESTORE_PORT)
    }

    /** Wipes every document and account, so each test starts from nothing. */
    fun reset() {
        delete("http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$projectId/databases/(default)/documents")
        delete("http://$HOST:$AUTH_PORT/emulator/v1/projects/$projectId/accounts")
    }

    private fun delete(url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "DELETE"
            check(conn.responseCode in 200..299) {
                "DELETE $url returned ${conn.responseCode}. Are the emulators running? Use scripts/emulator-tests.sh"
            }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * One phone: its own Firebase app instance, auth session and Firestore cache, pointed at the
 * emulators. Two of these in a test behave like two phones syncing through the server.
 */
class TestClient(private val email: String, private val emailVerified: Boolean = true) : Closeable {
    private val app = FirebaseApp.initializeApp(
        InstrumentationRegistry.getInstrumentation().targetContext,
        FirebaseApp.getInstance().options,
        "client-${UUID.randomUUID()}",
    )
    val auth: FirebaseAuth = FirebaseAuth.getInstance(app)
    val db: FirebaseFirestore = FirebaseFirestore.getInstance(app)

    init {
        Emulators.useFor(auth, db)
        // A fresh in-memory cache per client, so nothing leaks between tests.
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }

    val sessions = SessionRepository(db, ScoreRules(newId = { db.collection("_").document().id }))

    lateinit var uid: String
        private set

    /** Signs in as a Google user. The Auth emulator accepts an unsigned JSON ID token. */
    suspend fun signIn(): String {
        val idToken = JSONObject()
            .put("sub", "google-$email")
            .put("email", email)
            .put("email_verified", emailVerified)
            .toString()
        val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
        uid = user!!.uid
        return uid
    }

    // The app itself is left alive: once any FirebaseApp is deleted, Firebase Auth fails for
    // apps created after it ("FirebaseApp was deleted"). Each client gets a fresh name anyway.
    override fun close(): Unit = runBlocking {
        auth.signOut()
        db.terminate().await()
    }

    companion object {
        // The two accounts on the rules allow-list.
        const val PLAYER_A = "iambevan@gmail.com"
        const val PLAYER_B = "julianjones56@gmail.com"
    }
}

/** Waits for a value matching [predicate], failing the test with [what] if none arrives in time. */
suspend fun <T> Flow<T>.awaitValue(
    what: String,
    timeout: Duration = 15.seconds,
    predicate: (T) -> Boolean,
): T = withTimeoutOrNull(timeout) { first(predicate) } ?: run {
    fail("Timed out after $timeout waiting for: $what")
    error("unreachable")
}
