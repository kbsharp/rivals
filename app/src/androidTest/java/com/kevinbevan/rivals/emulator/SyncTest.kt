package com.kevinbevan.rivals.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.data.GuestRepository
import com.kevinbevan.rivals.data.LocalSessionStore
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.RivalryStatus
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Rivalry
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf
import kotlinx.coroutines.flow.map
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
 * Runs with real `firestore.rules`, so it also proves rivals can do everything they need to.
 */
@RunWith(AndroidJUnit4::class)
class SyncTest {
    private val race2 = MatchSettings(GameType.NINE_BALL, raceTo = 2)

    private lateinit var a: TestClient
    private lateinit var b: TestClient
    private lateinit var rivalry: Rivalry

    @Before
    fun setUp(): Unit = runBlocking {
        Emulators.reset()
        a = TestClient(TestClient.PLAYER_A)
        b = TestClient(TestClient.PLAYER_B)
        a.signUp()
        b.signUp()
        rivalry = TestClient.becomeRivals(a, b)
    }

    @After
    fun tearDown() {
        a.close()
        b.close()
    }

    @Test
    fun eachActionShowsUpLiveOnTheOtherPhone(): Unit = runBlocking {
        val id = a.start(rivalry, race2)
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
        val id = a.start(rivalry, race2)
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
        val first = a.start(rivalry, race2)
        b.sessions.observeSession(first).awaitValue("B sees A's session") { it.value != null }
        val second = b.start(rivalry, race2)
        assertEquals(first, second)
    }

    @Test
    fun endingASessionWithNothingPlayedDeletesItEverywhere(): Unit = runBlocking {
        val id = a.start(rivalry, race2)
        b.sessions.observeSession(id).awaitValue("B sees the session") { it.value != null }
        assertTrue(a.sessions.endSession(id))
        b.sessions.observeSession(id).awaitValue("B sees it deleted") { it.value == null }
        b.sessions.observeMatches(id).awaitValue("B sees its match deleted") { it.value.isEmpty() }
    }

    @Test
    fun deletingAPastSessionRemovesItAndEverythingInIt(): Unit = runBlocking {
        val id = a.start(rivalry, race2)
        repeat(3) { a.sessions.recordFrame(id, winnerId = a.uid, recordedBy = a.uid) } // 2–0, then 1–0
        assertEquals(false, a.sessions.endSession(id))
        b.sessions.observeSession(id).awaitValue("B sees it ended") { it.value?.status == Status.ENDED }

        b.sessions.deleteSession(id)
        a.sessions.observeSession(id).awaitValue("A sees it deleted") { it.value == null }
        assertEquals(0, a.db.collectionGroup("frames").whereArrayContains("playerIds", a.uid).get().await().size())
        assertEquals(0, a.db.collectionGroup("matches").whereArrayContains("playerIds", a.uid).get().await().size())
    }

    @Test
    fun statsSeeEveryMatchAndFrameAcrossSessions(): Unit = runBlocking {
        // Stats reads through collection-group queries, which the rules must allow.
        val first = a.start(rivalry, race2)
        repeat(2) { a.sessions.recordFrame(first, winnerId = a.uid, recordedBy = a.uid) }
        a.sessions.endSession(first)
        val second = a.start(rivalry, race2)
        a.sessions.recordFrame(second, winnerId = b.uid, recordedBy = a.uid)

        val frames = b.rivalries.observeAllFrames(b.uid).awaitValue("B sees all 3 frames") { it.size == 3 }
        assertEquals(mapOf(first to 2, second to 1), frames.groupingBy { it.sessionId }.eachCount())
        val matches = b.rivalries.observeAllMatches(b.uid).awaitValue("B sees both sessions' matches") {
            it.map { m -> m.sessionId }.toSet() == setOf(first, second)
        }
        assertEquals(a.uid, matches.single { it.sessionId == first }.match.winnerId)
    }

    @Test
    fun aGuestGameSavedToTheRivalryShowsUpForTheRival(): Unit = runBlocking {
        // Played on A's phone with no account, as a quick game.
        val local = LocalSessionStore(file = null)
        val rules = ScoreRules(newId = { a.db.collection("_").document().id })
        val guests = GuestRepository(local, rules)
        val guestSessions = SessionRepository(local, rules)
        val game = guests.startGame("Kev" to "Jules", race2)
        val (me, them) = GuestRepository.PLAYER_A to GuestRepository.PLAYER_B
        guestSessions.recordFrame(game, them, recordedBy = me)
        guestSessions.recordFrame(game, them, recordedBy = me) // Jules wins match 1
        guestSessions.recordFrame(game, me, recordedBy = me)
        guestSessions.endSession(game)

        a.rivalries.claimGuestGame(guests.docsOf(game), game, mapOf(me to a.uid, them to b.uid), rivalry)

        val session = b.sessions.observeSession(game).awaitValue("B gets the saved game") {
            it.value?.status == Status.ENDED
        }.value!!
        assertEquals(rivalry.id, session.rivalryId)
        assertEquals(mapOf(a.uid to 0, b.uid to 1), session.matchWins)
        val frames = b.rivalries.observeAllFrames(b.uid).awaitValue("B's stats see its frames") { it.size == 3 }
        assertEquals(setOf(game), frames.map { it.sessionId }.toSet())
    }

    @Test
    fun aShareLinkMakesRivalsWithoutAnEmailInvite(): Unit = runBlocking {
        TestClient("sam@example.com").use { sam ->
            sam.signUp()
            val code = a.rivalries.createInvite(a.player)
            val invite = sam.rivalries.loadInvite(code)!!
            assertEquals(a.uid, invite.from)
            val id = sam.rivalries.acceptInvite(sam.uid, invite)

            val r = a.rivalries.observeRivalry(id).awaitValue("A sees Sam accepted") { it?.status == RivalryStatus.ACTIVE }!!
            assertEquals(setOf(a.uid, sam.uid), r.playerIds.toSet())
            assertEquals(null, sam.rivalries.loadInvite(code)) // used up
            // And they can play straight away.
            sam.start(r, race2)
        }
    }

    @Test
    fun aDeclinedInviteIsGoneForBoth(): Unit = runBlocking {
        TestClient("sam@example.com").use { sam ->
            sam.signUp()
            a.rivalries.invite(a.uid, sam.uid)
            val id = Rivalry.idFor(a.uid, sam.uid)
            sam.rivalries.observeRivalry(id).awaitValue("Sam sees the invite") { it?.status == RivalryStatus.PENDING }
            sam.rivalries.remove(id)
            // (Mapped to a Boolean: awaitValue can't wait for a null.)
            a.rivalries.observeRivalry(id).map { it == null }.awaitValue("A sees it declined") { it }
        }
    }
}
