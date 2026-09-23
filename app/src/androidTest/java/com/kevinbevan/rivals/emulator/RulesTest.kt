package com.kevinbevan.rivals.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import com.kevinbevan.rivals.data.isPermissionDenied
import com.kevinbevan.rivals.domain.Schema
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Invite
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Rivalry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `firestore.rules` against the emulator: anyone can sign in, but a player only sees and
 * changes their own profile, their rivalries and the sessions they played in.
 */
@RunWith(AndroidJUnit4::class)
class RulesTest {
    private val race2 = MatchSettings(GameType.NINE_BALL, raceTo = 2)

    private lateinit var a: TestClient
    private lateinit var b: TestClient
    private lateinit var stranger: TestClient

    @Before
    fun setUp(): Unit = runBlocking {
        Emulators.reset()
        a = TestClient(TestClient.PLAYER_A).apply { signUp() }
        b = TestClient(TestClient.PLAYER_B).apply { signUp() }
        stranger = TestClient("stranger@example.com").apply { signUp() }
    }

    @After
    fun tearDown() {
        a.close()
        b.close()
        stranger.close()
    }

    // Finding players

    @Test
    fun aPlayerCanBeFoundByExactEmailButNotListed(): Unit = runBlocking {
        assertEquals(b.uid, stranger.players.findByEmail(TestClient.PLAYER_B.uppercase())?.uid)
        assertNull(stranger.players.findByEmail("nobody@example.com"))
        assertDenied { stranger.db.collection(Schema.PLAYERS).get().await() }
        assertDenied { stranger.db.collection(Schema.EMAILS).get().await() }
    }

    @Test
    fun nobodyCanClaimSomeoneElsesEmailOrProfile(): Unit = runBlocking {
        assertDenied {
            stranger.db.collection(Schema.EMAILS).document(TestClient.PLAYER_A).set(mapOf(Schema.UID to stranger.uid)).await()
        }
        assertDenied {
            stranger.db.collection(Schema.PLAYERS).document(a.uid).set(mapOf(Schema.DISPLAY_NAME to "Not A")).await()
        }
    }

    @Test
    fun anUnverifiedEmailCantBeIndexed(): Unit = runBlocking {
        TestClient("unverified@example.com", emailVerified = false).use { c ->
            c.signIn()
            assertDenied { c.players.upsert(c.player) }
        }
    }

    // Rivalries

    @Test
    fun aSessionNeedsAnAcceptedRivalry(): Unit = runBlocking {
        a.rivalries.invite(a.uid, b.uid)
        val pending = a.rivalries.observeRivalry(Rivalry.idFor(a.uid, b.uid)).awaitValue("A sees the invite") { it != null }!!
        // Written straight to the server, as the app's queued write would eventually be.
        assertDenied { a.db.collection(Schema.SESSIONS).document("s").set(session(a.uid, b.uid)).await() }

        // The inviter can't accept their own invite; the invited player can.
        assertDenied { a.rivalries.accept(pending.id) }
        b.rivalries.accept(pending.id)
        a.db.collection(Schema.SESSIONS).document("s").set(session(a.uid, b.uid)).await()
    }

    @Test
    fun strangersCantTouchARivalryOrItsSessions(): Unit = runBlocking {
        val rivalry = TestClient.becomeRivals(a, b)
        val id = a.start(rivalry, race2)
        a.sessions.recordFrame(id, a.uid, recordedBy = a.uid)
        b.sessions.observeSession(id).awaitValue("B sees the session") { it.value != null }

        assertDenied { stranger.db.collection(Schema.RIVALRIES).document(rivalry.id).get().await() }
        assertDenied { stranger.db.collection(Schema.RIVALRIES).document(rivalry.id).delete().await() }
        assertDenied { stranger.db.collection(Schema.SESSIONS).document(id).get().await() }
        assertDenied { stranger.db.collection(Schema.SESSIONS).document(id).collection(Schema.MATCHES).get().await() }
        assertDenied { stranger.db.collection(Schema.SESSIONS).get().await() }
        assertDenied { stranger.db.collectionGroup(Schema.FRAMES).get().await() }
        assertEquals(0, stranger.db.collectionGroup(Schema.FRAMES).whereArrayContains(Schema.PLAYER_IDS, stranger.uid).get().await().size())
        assertDenied { stranger.db.collection(Schema.SESSIONS).document(id).update(Schema.STATUS, "ended").await() }
    }

    @Test
    fun aStrangerCantStartASessionInSomeoneElsesRivalryOrRigThePlayers(): Unit = runBlocking {
        val rivalry = TestClient.becomeRivals(a, b)
        assertDenied {
            stranger.db.collection(Schema.SESSIONS).document("s").set(session(stranger.uid, b.uid, rivalryId = rivalry.id)).await()
        }
        // A member can't swap a stranger into their rivalry's session either.
        assertDenied { a.db.collection(Schema.SESSIONS).document("s").set(session(a.uid, stranger.uid, rivalryId = rivalry.id)).await() }
        // Nor invent a rivalry nobody accepted.
        assertDenied {
            stranger.db.collection(Schema.RIVALRIES).document(Rivalry.idFor(stranger.uid, a.uid)).set(
                mapOf(
                    Schema.PLAYER_IDS to listOf(stranger.uid, a.uid).sorted(),
                    Schema.STATUS to "active",
                    Schema.INVITED_BY to a.uid,
                ),
            ).await()
        }
    }

    @Test
    fun aDeclinedInviteIsGoneForBoth(): Unit = runBlocking {
        a.rivalries.invite(a.uid, b.uid)
        val id = Rivalry.idFor(a.uid, b.uid)
        b.rivalries.remove(id)
        // (Mapped to a Boolean: awaitValue can't wait for a null.)
        a.rivalries.observeRivalry(id).map { it == null }.awaitValue("A sees it declined") { it }
    }

    // Invite links

    @Test
    fun anInviteCanBeReadSignedOutButOnlyUsedOnce(): Unit = runBlocking {
        val code = a.rivalries.createInvite(a.player)
        TestClient("signed-out@example.com").use { c ->
            assertEquals("iambevan", c.rivalries.loadInvite(code)?.fromName)
        }
        b.rivalries.acceptInvite(b.uid, b.rivalries.loadInvite(code)!!)
        assertNull(stranger.rivalries.loadInvite(code))
        // A made-up invite gets nobody anywhere.
        assertDenied { stranger.rivalries.acceptInvite(stranger.uid, Invite("FAKECODE", a.uid, "A")) }
    }

    @Test
    fun anInviteCantBeForgedForSomeoneElse(): Unit = runBlocking {
        assertDenied {
            stranger.db.collection(Schema.INVITES).document("ABCDEFGH").set(
                mapOf(Schema.FROM to a.uid, Schema.FROM_NAME to "A", Schema.CREATED_AT to FieldValue.serverTimestamp()),
            ).await()
        }
    }

    @Test
    fun signedOutSeesNothingPrivate(): Unit = runBlocking {
        TestClient(TestClient.PLAYER_A).use { c ->
            assertDenied { c.db.collection(Schema.PLAYERS).document(a.uid).get().await() }
            assertDenied { c.db.collection(Schema.SESSIONS).whereArrayContains(Schema.PLAYER_IDS, a.uid).get().await() }
        }
    }

    private fun session(me: String, rival: String, rivalryId: String = Rivalry.idFor(me, rival)) = mapOf(
        Schema.PLAYER_IDS to listOf(me, rival),
        Schema.STATUS to "active",
        Schema.RIVALRY_ID to rivalryId,
        Schema.CREATED_BY to me,
        Schema.MATCH_WINS to mapOf(me to 0, rival to 0),
    )

    private suspend fun assertDenied(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            assertTrue("Expected PERMISSION_DENIED, got $e", e.isPermissionDenied())
            return
        }
        fail("Expected PERMISSION_DENIED, but it was allowed")
    }
}
