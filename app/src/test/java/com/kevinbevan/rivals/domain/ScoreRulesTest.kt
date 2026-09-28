package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.domain.DocPath.FrameDoc
import com.kevinbevan.rivals.domain.DocPath.MatchDoc
import com.kevinbevan.rivals.domain.DocPath.SessionDoc
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScoreRulesTest {
    private val a = "uidA"
    private val b = "uidB"
    private val race3 = MatchSettings(GameType.NINE_BALL, raceTo = 3)
    private val open = MatchSettings(GameType.NINE_BALL, raceTo = null)

    private lateinit var store: FakeStore
    private lateinit var rules: ScoreRules
    private var nextId = 0

    @Before
    fun setUp() {
        store = FakeStore()
        nextId = 0
        rules = ScoreRules { "id${++nextId}" }
    }

    // Helpers that play the app's part: read current state, plan, commit.

    private fun start(settings: MatchSettings = race3, venue: String? = null): String {
        val outcome = rules.startSession(listOf(a, b), createdBy = a, settings, venue)
        store.apply(outcome.plan)
        return outcome.sessionId
    }

    private fun win(sessionId: String, winner: String, breaker: String? = null): RecordOutcome {
        val outcome = rules.recordFrame(
            store.session(sessionId), store.activeMatch(sessionId)!!, winner, recordedBy = a, breaker,
        )
        store.apply(outcome.plan)
        return outcome
    }

    private fun undo(sessionId: String): Boolean {
        val (latest, previous) = store.latestTwo(sessionId)
        val plan = rules.undoLastFrame(store.session(sessionId), latest, previous) ?: return false
        store.apply(plan)
        return true
    }

    private fun score(sessionId: String) = store.activeMatch(sessionId)!!.frameWins

    // startSession

    @Test
    fun startSessionCreatesActiveSessionAndFirstMatch() {
        val id = start(venue = "  The Crown  ")
        val session = store.session(id)
        assertEquals(Status.ACTIVE, session.status)
        assertEquals(mapOf(a to 0, b to 0), session.matchWins)
        assertEquals("The Crown", session.venue)
        assertEquals(FakeStore.TIMESTAMP, store.docs.getValue(SessionDoc(id))[Schema.STARTED_AT])

        val match = store.matches(id).single()
        assertEquals(1, match.number)
        assertEquals(race3, match.settings)
        assertEquals(Status.ACTIVE, match.status)
        assertEquals(mapOf(a to 0, b to 0), match.frameWins)
    }

    @Test
    fun matchesAndFramesCarryThePlayersForStatsQueries() {
        val id = start()
        win(id, a)
        store.docs.filterKeys { it is MatchDoc || it is FrameDoc }.values.forEach {
            assertEquals(listOf(a, b), it[Schema.PLAYER_IDS])
        }
    }

    @Test
    fun aRivalsSessionRecordsItsRivalryAndAGuestGameItsNames() {
        val rivals = rules.startSession(listOf(a, b), a, race3, rivalryId = "a_b")
        store.apply(rivals.plan)
        assertEquals("a_b", store.session(rivals.sessionId).rivalryId)
        assertTrue(store.session(rivals.sessionId).names.isEmpty())

        val guest = rules.startSession(listOf(a, b), a, race3, names = mapOf(a to "Tom", b to "Sam"))
        store.apply(guest.plan)
        assertEquals(null, store.session(guest.sessionId).rivalryId)
        assertEquals(mapOf(a to "Tom", b to "Sam"), store.session(guest.sessionId).names)
    }

    @Test
    fun startSessionOmitsBlankVenue() {
        val id = start(venue = " ")
        assertFalse(Schema.VENUE in store.docs.getValue(SessionDoc(id)))
    }

    @Test
    fun startSessionRejectsBadPlayers() {
        assertThrows(IllegalArgumentException::class.java) { rules.startSession(listOf(a, a), a, race3) }
        assertThrows(IllegalArgumentException::class.java) { rules.startSession(listOf(a), a, race3) }
        assertThrows(IllegalArgumentException::class.java) { rules.startSession(listOf(a, b), "c", race3) }
    }

    @Test
    fun raceToMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { MatchSettings(GameType.TEN_BALL, 0) }
    }

    // recordFrame

    @Test
    fun recordingAFrameWritesTheFrameAndIncrementsTheMatch() {
        val id = start()
        val outcome = win(id, a, breaker = b)

        assertFalse(outcome.matchEnded)
        assertNull(outcome.matchWinnerId)
        assertNull(outcome.nextMatchId)
        assertEquals(2, outcome.plan.size)
        val update = outcome.plan[1] as Write.Update
        assertEquals(mapOf("frameWins.uidA" to FieldOp.Increment(1)), update.fields)

        assertEquals(mapOf(a to 1, b to 0), score(id))
        val frame = store.frames(id, store.activeMatch(id)!!.id).single()
        assertEquals(1, frame.number)
        assertEquals(a, frame.winnerId)
        assertEquals(b, frame.breakerId)
        assertEquals(a, frame.recordedBy)
    }

    @Test
    fun framesAreNumberedInOrder() {
        val id = start()
        win(id, a); win(id, b); win(id, b)
        val matchId = store.activeMatch(id)!!.id
        assertEquals(listOf(1, 2, 3), store.frames(id, matchId).map { it.number })
        assertEquals(listOf(a, b, b), store.frames(id, matchId).map { it.winnerId })
    }

    @Test
    fun hittingTheRaceOnTheLastFrameEndsTheMatchAndStartsTheNext() {
        val id = start()
        win(id, a); win(id, b); win(id, a); win(id, b)
        assertEquals(mapOf(a to 2, b to 2), score(id))

        val outcome = win(id, b)
        assertTrue(outcome.matchEnded)
        assertEquals(b, outcome.matchWinnerId)

        val (first, second) = store.matches(id)
        assertEquals(Status.ENDED, first.status)
        assertEquals(b, first.winnerId)
        assertEquals(mapOf(a to 2, b to 3), first.frameWins)
        assertEquals(FakeStore.TIMESTAMP, store.docs.getValue(MatchDoc(id, first.id))[Schema.ENDED_AT])

        assertEquals(outcome.nextMatchId, second.id)
        assertEquals(2, second.number)
        assertEquals(race3, second.settings)
        assertEquals(Status.ACTIVE, second.status)
        assertEquals(mapOf(a to 0, b to 0), second.frameWins)

        assertEquals(mapOf(a to 0, b to 1), store.session(id).matchWins)
    }

    @Test
    fun raceToOneEndsOnEveryFrame() {
        val id = start(MatchSettings(GameType.TEN_BALL, raceTo = 1))
        assertTrue(win(id, a).matchEnded)
        assertTrue(win(id, a).matchEnded)
        assertTrue(win(id, b).matchEnded)
        assertEquals(mapOf(a to 2, b to 1), store.session(id).matchWins)
        assertEquals(4, store.matches(id).size)
    }

    @Test
    fun openEndedMatchNeverEndsOnItsOwn() {
        val id = start(open)
        repeat(50) { assertFalse(win(id, a).matchEnded) }
        assertEquals(mapOf(a to 50, b to 0), score(id))
        assertEquals(1, store.matches(id).size)
    }

    @Test
    fun recordingRejectsStrangersAndEndedMatches() {
        val id = start()
        val session = store.session(id)
        val match = store.activeMatch(id)!!
        assertThrows(IllegalArgumentException::class.java) { rules.recordFrame(session, match, "c", a) }
        assertThrows(IllegalArgumentException::class.java) { rules.recordFrame(session, match, a, "c") }
        assertThrows(IllegalArgumentException::class.java) { rules.recordFrame(session, match, a, a, "c") }
        assertThrows(IllegalStateException::class.java) {
            rules.recordFrame(session, match.copy(status = Status.ENDED), a, a)
        }
        assertThrows(IllegalStateException::class.java) {
            rules.recordFrame(session.copy(status = Status.ENDED), match, a, a)
        }
    }

    // undoLastFrame

    @Test
    fun undoWithNothingRecordedDoesNothing() {
        val id = start()
        val before = store.docs.toMap()
        assertFalse(undo(id))
        assertEquals(before, store.docs.toMap())
    }

    @Test
    fun undoRemovesTheLastFrameOnly() {
        val id = start()
        win(id, a); win(id, b)
        assertTrue(undo(id))
        assertEquals(mapOf(a to 1, b to 0), score(id))
        val frames = store.frames(id, store.activeMatch(id)!!.id)
        assertEquals(listOf(a), frames.map { it.winnerId })
    }

    @Test
    fun undoAcrossAMatchBoundaryRestoresTheExactPreviousState() {
        val id = start()
        win(id, a); win(id, a)
        val beforeWinningFrame = deepCopy(store.docs)

        win(id, a) // ends match 1 and starts match 2
        assertEquals(2, store.matches(id).size)

        assertTrue(undo(id))
        assertEquals(beforeWinningFrame, store.docs)

        val match = store.activeMatch(id)!!
        assertEquals(1, match.number)
        assertNull(match.winnerId)
        assertEquals(mapOf(a to 0, b to 0), store.session(id).matchWins)
    }

    @Test
    fun repeatedUndoWalksBackThroughSeveralMatches() {
        val id = start(MatchSettings(GameType.NINE_BALL, raceTo = 2))
        val snapshots = mutableListOf(deepCopy(store.docs))
        for (w in listOf(a, b, b, a, a, b, a)) {
            win(id, w)
            snapshots += deepCopy(store.docs)
        }
        assertEquals(mapOf(a to 1, b to 1), store.session(id).matchWins)

        // Undo one frame at a time and land on each earlier state in turn.
        for (expected in snapshots.dropLast(1).reversed()) {
            assertTrue(undo(id))
            assertEquals(expected, store.docs)
        }
        assertFalse(undo(id))
    }

    @Test
    fun undoAfterUndoingIntoAPreviousMatchKeepsGoing() {
        val id = start(MatchSettings(GameType.NINE_BALL, raceTo = 1))
        win(id, a) // match 1 -> a, match 2 starts
        win(id, b) // match 2 -> b, match 3 starts
        assertTrue(undo(id)) // back into match 2
        assertTrue(undo(id)) // match 2 now empty and deleted; back into match 1
        assertEquals(1, store.matches(id).size)
        assertEquals(mapOf(a to 0, b to 0), score(id))
        assertEquals(mapOf(a to 0, b to 0), store.session(id).matchWins)
    }

    @Test
    fun undoReopensAMatchThatWasEndedByHand() {
        val id = start(open)
        win(id, a); win(id, a); win(id, b)
        store.apply(rules.endMatch(store.session(id), store.activeMatch(id)!!).plan)
        assertEquals(mapOf(a to 1, b to 0), store.session(id).matchWins)
        assertNull(store.activeMatch(id))

        assertTrue(undo(id))
        val match = store.activeMatch(id)!!
        assertEquals(mapOf(a to 2, b to 0), match.frameWins)
        assertNull(match.winnerId)
        assertEquals(mapOf(a to 0, b to 0), store.session(id).matchWins)
    }

    @Test
    fun undoInAnEndedSessionIsRejected() {
        val id = start()
        win(id, a)
        val (latest, previous) = store.latestTwo(id)
        val ended = store.session(id).copy(status = Status.ENDED)
        assertThrows(IllegalStateException::class.java) { rules.undoLastFrame(ended, latest, previous) }
    }

    // endMatch / startMatch

    @Test
    fun endingAnOpenEndedMatchGivesItToTheLeader() {
        val id = start(open)
        win(id, b); win(id, a); win(id, b)
        val outcome = rules.endMatch(store.session(id), store.activeMatch(id)!!, next = race3)
        store.apply(outcome.plan)

        assertEquals(b, outcome.winnerId)
        val (first, second) = store.matches(id)
        assertEquals(Status.ENDED, first.status)
        assertEquals(b, first.winnerId)
        assertEquals(outcome.nextMatchId, second.id)
        assertEquals(race3, second.settings)
        assertEquals(mapOf(a to 0, b to 1), store.session(id).matchWins)
    }

    @Test
    fun endingALevelOrAbandonedMatchHasNoWinner() {
        val level = start(open)
        win(level, a); win(level, b)
        val levelOutcome = rules.endMatch(store.session(level), store.activeMatch(level)!!)
        store.apply(levelOutcome.plan)
        assertNull(levelOutcome.winnerId)
        assertEquals(mapOf(a to 0, b to 0), store.session(level).matchWins)

        val abandoned = start(race3)
        win(abandoned, a); win(abandoned, a)
        val abandonedOutcome = rules.endMatch(store.session(abandoned), store.activeMatch(abandoned)!!)
        store.apply(abandonedOutcome.plan)
        assertNull(abandonedOutcome.winnerId)
        assertEquals(mapOf(a to 0, b to 0), store.session(abandoned).matchWins)
        assertFalse(Schema.WINNER_ID in store.docs.getValue(MatchDoc(abandoned, store.matches(abandoned)[0].id)))
    }

    @Test
    fun manualWinnerRules() {
        fun m(raceTo: Int?, wa: Int, wb: Int) =
            Match("m", 1, MatchSettings(GameType.TEN_BALL, raceTo), Status.ACTIVE, mapOf(a to wa, b to wb))
        assertEquals(a, ScoreRules.manualWinner(m(null, 3, 1)))
        assertEquals(b, ScoreRules.manualWinner(m(null, 0, 1)))
        assertNull(ScoreRules.manualWinner(m(null, 2, 2)))
        assertNull(ScoreRules.manualWinner(m(null, 0, 0)))
        assertNull(ScoreRules.manualWinner(m(5, 4, 0)))
    }

    @Test
    fun startMatchAfterAHandEndedMatch() {
        val id = start(open)
        win(id, a)
        store.apply(rules.endMatch(store.session(id), store.activeMatch(id)!!).plan)

        val (plan, matchId) = rules.startMatch(store.session(id), store.matches(id).last(), race3)
        store.apply(plan)
        val match = store.activeMatch(id)!!
        assertEquals(matchId, match.id)
        assertEquals(2, match.number)
        assertEquals(race3, match.settings)
    }

    @Test
    fun startMatchRefusesWhileOneIsRunning() {
        val id = start()
        assertThrows(IllegalStateException::class.java) {
            rules.startMatch(store.session(id), store.activeMatch(id), race3)
        }
    }

    // Scoring

    @Test
    fun theStarterScoresUntilTheOtherPlayerTakesOver() {
        val id = start()
        assertEquals(a, store.session(id).scorerId)
        store.apply(rules.takeOverScoring(store.session(id), b))
        assertEquals(b, store.session(id).scorerId)
        assertThrows(IllegalArgumentException::class.java) { rules.takeOverScoring(store.session(id), "stranger") }
    }

    @Test
    fun endingASessionFromTheWatchingPhoneTakesOverScoring() {
        val id = start()
        win(id, a)
        store.apply(rules.endSession(store.session(id), store.matches(id), endedBy = b))
        assertEquals(Status.ENDED, store.session(id).status)
        assertEquals(b, store.session(id).scorerId)
    }

    // endSession

    @Test
    fun endingASessionDropsTheEmptyFollowOnMatch() {
        val id = start(MatchSettings(GameType.NINE_BALL, raceTo = 1))
        win(id, a)
        store.apply(rules.endSession(store.session(id), store.matches(id)))

        val session = store.session(id)
        assertEquals(Status.ENDED, session.status)
        assertEquals(FakeStore.TIMESTAMP, store.docs.getValue(SessionDoc(id))[Schema.ENDED_AT])
        assertEquals(1, store.matches(id).size)
        assertEquals(mapOf(a to 1, b to 0), session.matchWins)
    }

    @Test
    fun endingASessionEndsAMatchInProgress() {
        val id = start(open)
        win(id, b)
        store.apply(rules.endSession(store.session(id), store.matches(id)))
        val match = store.matches(id).single()
        assertEquals(Status.ENDED, match.status)
        assertEquals(b, match.winnerId)
        assertEquals(mapOf(a to 0, b to 1), store.session(id).matchWins)
    }

    @Test
    fun endingASessionTwiceIsRejected() {
        val id = start()
        win(id, a)
        store.apply(rules.endSession(store.session(id), store.matches(id)))
        assertThrows(IllegalStateException::class.java) { rules.endSession(store.session(id), store.matches(id)) }
    }

    // changeSettings

    @Test
    fun theRaceWinningFrameCanBeTaggedAfterTheNextMatchStarts() {
        val id = start(MatchSettings(GameType.NINE_BALL, raceTo = 1))
        win(id, a)
        // The session's last frame is in the previous (ended) match; the new one is empty.
        val (latest, previous) = store.latestTwo(id)
        assertNull(latest.lastFrame)
        val frame = previous!!.lastFrame!!
        store.apply(rules.setFrameEvents(store.session(id), previous.match, frame, setOf(FrameEvent.GOLDEN_BREAK)))
        assertEquals(setOf(FrameEvent.GOLDEN_BREAK), store.frames(id, previous.match.id).single().events)

        val tagged = store.frames(id, previous.match.id).single()
        store.apply(rules.setFrameEvents(store.session(id), previous.match, tagged, emptySet()))
        assertEquals(emptySet<FrameEvent>(), store.frames(id, previous.match.id).single().events)
    }

    @Test
    fun eventsAreStoredInAStableOrderAndUnknownOnesAreDropped() {
        val id = start()
        win(id, a)
        val match = store.activeMatch(id)!!
        val frame = store.frames(id, match.id).single()
        val plan = rules.setFrameEvents(store.session(id), match, frame, setOf(FrameEvent.GOLDEN_BREAK, FrameEvent.BREAK_AND_RUN))
        assertEquals(listOf("break-and-run", "golden-break"), (plan.single() as Write.Update).fields[Schema.EVENTS])
        val read = com.kevinbevan.rivals.data.frameFrom("f", mapOf(Schema.EVENTS to listOf("golden-break", "trick-shot")))
        assertEquals(setOf(FrameEvent.GOLDEN_BREAK), read.events)
    }

    @Test
    fun framesInAnEndedSessionCantBeTagged() {
        val id = start()
        win(id, a)
        val match = store.activeMatch(id)!!
        val frame = store.frames(id, match.id).single()
        store.apply(rules.endSession(store.session(id), store.matches(id)))
        assertThrows(IllegalStateException::class.java) {
            rules.setFrameEvents(store.session(id), match, frame, setOf(FrameEvent.BREAK_AND_RUN))
        }
    }

    @Test
    fun changeSettingsRewritesAnEmptyMatch() {
        val id = start()
        val match = store.activeMatch(id)!!
        store.apply(rules.changeSettings(store.session(id), match, open))
        val changed = store.activeMatch(id)!!
        assertEquals(open, changed.settings)
        assertEquals(match.number, changed.number)
        assertEquals(mapOf(a to 0, b to 0), changed.frameWins)
    }

    @Test
    fun changeSettingsWorksMidMatchButNeverBelowTheLeader() {
        val id = start(MatchSettings(GameType.NINE_BALL, raceTo = 5))
        win(id, a); win(id, a); win(id, a); win(id, b)
        val match = store.activeMatch(id)!!
        assertEquals(4, ScoreRules.minRace(match))
        assertThrows(IllegalStateException::class.java) {
            rules.changeSettings(store.session(id), match, MatchSettings(GameType.NINE_BALL, raceTo = 3))
        }
        store.apply(rules.changeSettings(store.session(id), match, MatchSettings(GameType.TEN_BALL, raceTo = 4)))
        val changed = store.activeMatch(id)!!
        assertEquals(MatchSettings(GameType.TEN_BALL, raceTo = 4), changed.settings)
        assertEquals(mapOf(a to 3, b to 1), changed.frameWins)
        // Open-ended is always allowed, and the next frame to the new race ends the match.
        store.apply(rules.changeSettings(store.session(id), changed, open))
        assertEquals(open, store.activeMatch(id)!!.settings)
    }

    @Test
    fun endingASessionWithNothingPlayedDeletesIt() {
        val id = start()
        store.apply(rules.endSession(store.session(id), store.matches(id)))
        assertTrue(store.docs.isEmpty())
    }

    @Test
    fun endingASessionAfterAnUndoneFrameDeletesIt() {
        val id = start()
        win(id, a)
        undo(id)
        store.apply(rules.endSession(store.session(id), store.matches(id)))
        assertTrue(store.docs.isEmpty())
    }

    // deleteSession

    @Test
    fun deletingASessionRemovesEveryDocInIt() {
        val id = start(MatchSettings(GameType.NINE_BALL, raceTo = 2))
        win(id, a); win(id, b); win(id, a) // match 1 to A, match 2 started
        win(id, b)
        store.apply(rules.endSession(store.session(id), store.matches(id)))
        val matches = store.matches(id).map { MatchWithFrames(it, store.frames(id, it.id)) }
        assertEquals(4, matches.sumOf { it.frames.size })

        store.apply(rules.deleteSession(store.session(id), matches))
        assertTrue(store.docs.isEmpty())
    }

    @Test
    fun deletingASessionThatIsStillRunningIsRejected() {
        val id = start()
        assertThrows(IllegalStateException::class.java) { rules.deleteSession(store.session(id), emptyList()) }
    }

    // headToHead

    @Test
    fun headToHeadSumsEverySession() {
        fun s(wa: Int, wb: Int, status: Status) =
            Session("s", listOf(a, b), status, null, createdBy = a, matchWins = mapOf(a to wa, b to wb))
        val total = ScoreRules.headToHead(
            listOf(s(3, 1, Status.ENDED), s(0, 2, Status.ENDED), s(1, 1, Status.ACTIVE)),
        )
        assertEquals(mapOf(a to 4, b to 4), total)
        assertEquals(emptyMap<String, Int>(), ScoreRules.headToHead(emptyList()))
    }

    // FakeStore sanity: the plan shapes the repository will rely on.

    @Test
    fun plansOnlyUseDottedKeysInUpdates() {
        val id = start(MatchSettings(GameType.NINE_BALL, raceTo = 1))
        val outcome = win(id, a)
        val frameWrite = outcome.plan.first() as Write.Set
        assertTrue(frameWrite.doc is FrameDoc)
        assertTrue(outcome.plan.filterIsInstance<Write.Set>().all { w -> w.fields.keys.none { '.' in it } })
    }

    @Suppress("UNCHECKED_CAST")
    private fun deepCopy(docs: Docs): Docs =
        docs.mapValues { (_, d) -> d.mapValues { (_, v) -> if (v is Map<*, *>) v.toMap() else v } }
}
