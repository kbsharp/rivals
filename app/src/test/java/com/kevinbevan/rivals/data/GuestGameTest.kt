package com.kevinbevan.rivals.data

import com.kevinbevan.rivals.domain.DocPath.FrameDoc
import com.kevinbevan.rivals.domain.DocPath.MatchDoc
import com.kevinbevan.rivals.domain.DocPath.SessionDoc
import com.kevinbevan.rivals.domain.GuestClaim
import com.kevinbevan.rivals.domain.PlanApplier
import com.kevinbevan.rivals.domain.Schema
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Rivalry
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guest games on the phone: played through [SessionRepository], saved to JSON, claimed into a rivalry. */
class GuestGameTest {
    private var nextId = 0
    private val rules = ScoreRules(newId = { "id${nextId++}" })
    private val clock = Instant.parse("2026-09-22T19:30:00Z")
    private val race2 = MatchSettings(GameType.NINE_BALL, raceTo = 2)
    private val a = GuestRepository.PLAYER_A
    private val b = GuestRepository.PLAYER_B

    private fun TestScope.store(file: File? = null) = LocalSessionStore(file, now = { clock }, scope = this)

    @Test
    fun aGuestNightPlaysLikeARivalsOne() = runTest {
        val store = store()
        val guests = GuestRepository(store, rules)
        val sessions = SessionRepository(store, rules)
        val id = guests.startGame("Tom" to " ", race2)

        val session = store.loadSession(id)
        assertEquals(mapOf(a to "Tom", b to "Player 2"), session.names)
        assertNull(session.rivalryId)
        assertEquals(clock, session.startedAt)

        sessions.recordFrame(id, a, recordedBy = a)
        sessions.recordFrame(id, a, recordedBy = a)
        assertTrue(sessions.toggleLastFrameEvent(id, FrameEvent.BREAK_AND_RUN))
        assertEquals(1, store.loadSession(id).matchWins.winsOf(a))
        assertEquals(2, store.loadMatches(id).size)

        assertTrue(sessions.undoLastFrame(id))
        assertEquals(0, store.loadSession(id).matchWins.winsOf(a))
        sessions.recordFrame(id, b, recordedBy = a)
        sessions.endSession(id)
        assertEquals(Status.ENDED, store.loadSession(id).status)
    }

    @Test
    fun startingAgainResumesTheRunningGame() = runTest {
        val guests = GuestRepository(store(), rules)
        val first = guests.startGame("A" to "B", race2)
        assertEquals(first, guests.startGame("C" to "D", race2))
    }

    @Test
    fun gamesSurviveARestart() = runTest {
        val file = Files.createTempFile("guest", ".json").toFile().also { it.delete() }
        val store = store(file)
        val id = GuestRepository(store, rules).startGame("Tom" to "Sam", race2)
        SessionRepository(store, rules).recordFrame(id, b, recordedBy = a)
        advanceUntilIdle()

        val reopened = store(file)
        assertEquals(store.docs.value, reopened.docs.value)
        assertEquals(1, reopened.loadMatches(id).single().frameWins.winsOf(b))
        assertEquals(clock, reopened.loadSession(id).startedAt)
        file.delete()
    }

    @Test
    fun aCorruptFileIsSetAsideNotFatal() = runTest {
        val dir = Files.createTempDirectory("guest").toFile()
        val file = File(dir, "guest-games.json").apply { writeText("{not json") }
        assertTrue(store(file).docs.value.isEmpty())
        assertFalse(file.exists())
        assertEquals(1, dir.listFiles()!!.count { it.name.startsWith("guest-games.json.corrupt") })
        dir.deleteRecursively()
    }

    @Test
    fun deletingAGameRemovesEverythingInIt() = runTest {
        val store = store()
        val guests = GuestRepository(store, rules)
        val id = guests.startGame("A" to "B", race2)
        SessionRepository(store, rules).recordFrame(id, a, recordedBy = a)
        guests.deleteGame(id)
        assertTrue(store.docs.value.isEmpty())
    }

    @Test
    fun claimingSwapsGuestIdsForUids() = runTest {
        val store = store()
        val guests = GuestRepository(store, rules)
        val sessions = SessionRepository(store, rules)
        val id = guests.startGame("Kev" to "Jules", race2)
        sessions.recordFrame(id, b, recordedBy = a)
        sessions.recordFrame(id, b, recordedBy = a)
        sessions.recordFrame(id, a, recordedBy = a)
        sessions.endSession(id)

        val rivalry = Rivalry.idFor("uidK", "uidJ")
        val plan = GuestClaim.plan(guests.docsOf(id), id, mapOf(a to "uidK", b to "uidJ"), rivalry)

        // The session first, so a split plan commits it before its matches and frames.
        assertEquals(SessionDoc(id), plan.first().doc)
        val claimed = PlanApplier.apply(emptyMap(), plan, "ts")
        val s = sessionFrom(id, claimed.getValue(SessionDoc(id)))
        assertEquals(listOf("uidK", "uidJ"), s.playerIds)
        assertEquals(rivalry, s.rivalryId)
        assertEquals("uidK", s.createdBy)
        assertEquals(mapOf("uidK" to 0, "uidJ" to 1), s.matchWins)
        assertTrue(s.names.isEmpty())
        assertEquals(clock, s.startedAt)

        val matches = claimed.filterKeys { it is MatchDoc }.map { (p, d) -> matchFrom((p as MatchDoc).matchId, d) }
        val won = matches.single { it.number == 1 }
        assertEquals("uidJ", won.winnerId)
        assertEquals(mapOf("uidK" to 0, "uidJ" to 2), won.frameWins)
        val frames = claimed.filterKeys { it is FrameDoc }
        assertEquals(3, frames.size)
        frames.values.forEach { f ->
            assertEquals(listOf("uidK", "uidJ"), f[Schema.PLAYER_IDS])
            assertEquals("uidK", f[Schema.RECORDED_BY])
            assertTrue(f[Schema.WINNER_ID] in listOf("uidK", "uidJ"))
        }
        claimed.filterKeys { it is MatchDoc }.values.forEach { assertEquals(listOf("uidK", "uidJ"), it[Schema.PLAYER_IDS]) }
    }

    @Test
    fun claimingNeedsBothPlayersMappedToDifferentUids() = runTest {
        val store = store()
        val guests = GuestRepository(store, rules)
        val id = guests.startGame("A" to "B", race2)
        assertThrows(IllegalArgumentException::class.java) { GuestClaim.plan(guests.docsOf(id), id, mapOf(a to "u"), "r") }
        assertThrows(IllegalArgumentException::class.java) {
            GuestClaim.plan(guests.docsOf(id), id, mapOf(a to "u", b to "u"), "r")
        }
    }
}
