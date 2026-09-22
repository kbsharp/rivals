package com.kevinbevan.rivals.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Phase 5 exit test, automated: two "phones" (separate Firebase clients) playing through
 * [com.kevinbevan.rivals.data.SessionRepository] against the Firestore and Auth emulators.
 *
 * Runs with real `firestore.rules`, so it also proves both allowed accounts can play.
 */
@RunWith(AndroidJUnit4::class)
class SyncTest {
    private val race2 = MatchSettings(GameType.EIGHT_BALL, raceTo = 2)

    private lateinit var a: TestClient
    private lateinit var b: TestClient

    @Before
    fun setUp(): Unit = runBlocking {
        Emulators.reset()
        a = TestClient(TestClient.PLAYER_A)
        b = TestClient(TestClient.PLAYER_B)
        a.signIn()
        b.signIn()
    }

    @After
    fun tearDown() {
        a.close()
        b.close()
    }

    @Test
    fun eachActionShowsUpLiveOnTheOtherPhone(): Unit = runBlocking {
        val id = a.sessions.startSession(a.uid, b.uid, race2)
        val onB = b.sessions.observeMatches(id)
        val onA = a.sessions.observeMatches(id)

        onB.awaitValue("B sees match 1") { it.value.singleOrNull()?.number == 1 }

        a.sessions.recordFrame(id, winnerId = a.uid, recordedBy = a.uid)
        onB.awaitValue("B sees A's frame") { it.value.single().frameWins.winsOf(a.uid) == 1 }

        // Either phone can record.
        b.sessions.recordFrame(id, winnerId = b.uid, recordedBy = b.uid)
        onA.awaitValue("A sees B's frame") { it.value.single().frameWins.winsOf(b.uid) == 1 }

        // A reaches the race: match 1 ends and match 2 starts, in one write.
        a.sessions.recordFrame(id, winnerId = a.uid, recordedBy = a.uid)
        val ended = onB.awaitValue("B sees match 1 end and match 2 start") { it.value.size == 2 }.value
        assertEquals(Status.ENDED, ended[0].status)
        assertEquals(a.uid, ended[0].winnerId)
        assertEquals(Status.ACTIVE, ended[1].status)
        b.sessions.observeSession(id).awaitValue("B sees A's match win") {
            it.value?.matchWins?.winsOf(a.uid) == 1
        }

        // B undoes the winning frame: match 2 goes and match 1 reopens, on A too.
        assertTrue(b.sessions.undoLastFrame(id))
        onA.awaitValue("A sees the undo") {
            val m = it.value.singleOrNull()
            m != null && m.status == Status.ACTIVE && m.frameWins.winsOf(a.uid) == 1 && m.winnerId == null
        }
        a.sessions.observeSession(id).awaitValue("A sees the match win taken back") {
            it.value?.matchWins?.winsOf(a.uid) == 0
        }

        // B ends the session; A's screen would see it and leave.
        b.sessions.endSession(id)
        a.sessions.observeSession(id).awaitValue("A sees the session end") { it.value?.status == Status.ENDED }
    }

    @Test
    fun aNightPlayedOfflineSyncsOnReconnect(): Unit = runBlocking {
        a.db.disableNetwork().await()

        // Everything below happens with A offline: nothing waits on the server.
        val id = a.sessions.startSession(a.uid, b.uid, race2)
        a.sessions.recordFrame(id, winnerId = a.uid, recordedBy = a.uid)
        a.sessions.recordFrame(id, winnerId = b.uid, recordedBy = a.uid)
        a.sessions.recordFrame(id, winnerId = a.uid, recordedBy = a.uid) // A wins match 1
        assertTrue(a.sessions.undoLastFrame(id)) // ...takes it back, across the match boundary
        a.sessions.recordFrame(id, winnerId = a.uid, recordedBy = a.uid) // ...and wins it again
        a.sessions.recordFrame(id, winnerId = b.uid, recordedBy = a.uid) // match 2: B leads 1–0

        a.sessions.observeMatches(id).awaitValue("A shows its offline play as unsynced") {
            it.hasPendingWrites && it.value.size == 2
        }
        b.sessions.observeSession(id).awaitValue("B has heard nothing yet") { it.value == null }

        a.db.enableNetwork().await()

        b.sessions.observeSession(id).awaitValue("B gets the session") {
            it.value?.matchWins == mapOf(a.uid to 1, b.uid to 0)
        }
        // The queued batches reach the server one by one, so B may pass through in-between
        // states; what matters is that it lands on exactly what A played.
        val matches = b.sessions.observeMatches(id).awaitValue("B gets both matches, fully played") {
            it.value.size == 2 && it.value[1].frameWins == mapOf(a.uid to 0, b.uid to 1)
        }.value
        assertEquals(mapOf(a.uid to 2, b.uid to 1), matches[0].frameWins)
        assertEquals(Status.ENDED, matches[0].status)
        assertEquals(a.uid, matches[0].winnerId)
        assertEquals(Status.ACTIVE, matches[1].status)
        val frames = b.sessions.observeFrames(id, matches[0].id).awaitValue("B gets match 1's frames") {
            it.value.size == 3
        }.value
        assertEquals(listOf(a.uid, b.uid, a.uid), frames.map { it.winnerId })

        a.sessions.observeMatches(id).awaitValue("A's writes are confirmed") { !it.hasPendingWrites }
    }

    @Test
    fun startingWhenASessionIsActiveJoinsIt(): Unit = runBlocking {
        val first = a.sessions.startSession(a.uid, b.uid, race2)
        b.sessions.observeSession(first).awaitValue("B sees A's session") { it.value != null }
        val second = b.sessions.startSession(b.uid, a.uid, race2)
        assertEquals(first, second)
    }
}
