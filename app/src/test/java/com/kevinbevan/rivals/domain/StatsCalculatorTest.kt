package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Plays whole nights through [ScoreRules] and the [FakeStore], then checks the stats against
 * a hand count, so the calculator is tested on exactly the documents the app writes.
 */
class StatsCalculatorTest {
    private val a = "uidA"
    private val b = "uidB"
    private val ten2 = MatchSettings(GameType.TEN_BALL, raceTo = 2)
    private val nine1 = MatchSettings(GameType.NINE_BALL, raceTo = 1)

    private lateinit var store: FakeStore
    private lateinit var rules: ScoreRules
    private var nextId = 0
    private val startTimes = mutableMapOf<String, Instant>()

    @Before
    fun setUp() {
        store = FakeStore()
        nextId = 0
        rules = ScoreRules { "id${++nextId}" }
    }

    private fun night(startedAt: String, settings: MatchSettings, end: Boolean = true, play: Night.() -> Unit): String {
        val outcome = rules.startSession(listOf(a, b), a, settings)
        store.apply(outcome.plan)
        startTimes[outcome.sessionId] = Instant.parse(startedAt)
        Night(outcome.sessionId).play()
        if (end) store.apply(rules.endSession(store.session(outcome.sessionId), store.matches(outcome.sessionId)))
        return outcome.sessionId
    }

    private inner class Night(val id: String) {
        fun frame(winner: String, vararg events: FrameEvent) {
            val plan = rules.recordFrame(store.session(id), store.activeMatch(id)!!, winner, a).plan
            store.apply(plan)
            if (events.isEmpty()) return
            val doc = plan.first { it.doc is DocPath.FrameDoc }.doc as DocPath.FrameDoc
            val match = store.matches(id).single { it.id == doc.matchId }
            val frame = store.frames(id, doc.matchId).single { it.id == doc.frameId }
            store.apply(rules.setFrameEvents(store.session(id), match, frame, events.toSet()))
        }
        fun newGame(settings: MatchSettings) {
            store.apply(rules.changeSettings(store.session(id), store.activeMatch(id)!!, settings))
        }
    }

    private fun stats(me: String = a, rival: String = b): Stats {
        val sessionIds = store.docs.keys.filterIsInstance<DocPath.SessionDoc>().map { it.sessionId }
        val sessions = sessionIds.map { store.session(it).copy(startedAt = startTimes[it]) }
        val matches = sessionIds.flatMap { s -> store.matches(s).map { SessionMatch(s, it) } }
        val frames = matches.flatMap { m -> store.frames(m.sessionId, m.match.id).map { SessionFrame(m.sessionId, m.match.id, it) } }
        return StatsCalculator.compute(me, rival, sessions, matches, frames)
    }

    @Test
    fun nothingPlayedIsAllZeroes() {
        val s = stats()
        assertEquals(Record(0, 0), s.matches)
        assertNull(s.matches.winRate)
        assertEquals(Streak(null, 0), s.currentStreak)
        assertEquals(emptyMap<GameType, GameTypeStats>(), s.byGameType)
        assertEquals(FrameEvent.entries.associateWith { Count() }, s.specials)
    }

    @Test
    fun aRealisticFortnight() {
        // Night 1 (10-ball, race to 2): A wins 2–1, then B wins 2–0, then A wins 2–0. A takes the night 2–1.
        night("2026-09-01T19:00:00Z", ten2) {
            frame(a, FrameEvent.BREAK_AND_RUN); frame(b); frame(a, FrameEvent.BREAK_AND_RUN)
            frame(b, FrameEvent.GOLDEN_BREAK); frame(b)
            frame(a); frame(a, FrameEvent.BREAK_AND_RUN, FrameEvent.GOLDEN_BREAK)
        }
        // Night 2 (9-ball, race to 1): B, B, B. Then a frame of an unfinished race-to-2 10-ball match.
        night("2026-09-08T19:00:00Z", nine1) {
            frame(b); frame(b); frame(b)
            newGame(ten2)
            frame(a)
        }

        val s = stats()
        // Matches: A won 2, B won 4 (1 + 3). The unfinished match counts for neither.
        assertEquals(Record(won = 2, lost = 4), s.matches)
        // Frames: A 4 + 1 = 5, B 3 + 3 = 6.
        assertEquals(Record(won = 5, lost = 6), s.frames)
        assertEquals(NightsRecord(won = 1, lost = 1, drawn = 0), s.nights)

        assertEquals(Record(2, 1), s.byGameType.getValue(GameType.TEN_BALL).matches)
        assertEquals(Record(5, 3), s.byGameType.getValue(GameType.TEN_BALL).frames)
        assertEquals(Record(0, 3), s.byGameType.getValue(GameType.NINE_BALL).matches)
        assertEquals(setOf(GameType.NINE_BALL, GameType.TEN_BALL), s.byGameType.keys)

        // Match winners in order: A, B, A, B, B, B.
        assertEquals(Streak(b, 3), s.currentStreak)
        assertEquals(mapOf(a to 1, b to 3), s.longestStreaks)

        // Specials go to the frame winner: A ran out 3 times (one also a golden break), B had 1 golden break.
        assertEquals(Count(mine = 3, theirs = 0), s.specials.getValue(FrameEvent.BREAK_AND_RUN))
        assertEquals(Count(mine = 1, theirs = 1), s.specials.getValue(FrameEvent.GOLDEN_BREAK))
    }

    @Test
    fun theRivalsViewIsTheMirrorImage() {
        night("2026-09-01T19:00:00Z", ten2) { frame(a); frame(a); frame(b); frame(b); frame(a) }
        val mine = stats()
        val theirs = stats(me = b, rival = a)
        assertEquals(Record(mine.matches.lost, mine.matches.won), theirs.matches)
        assertEquals(Record(mine.frames.lost, mine.frames.won), theirs.frames)
        assertEquals(mine.currentStreak, theirs.currentStreak)
    }

    @Test
    fun streaksFollowTheOrderOfNightsNotOfIds() {
        // Played second but created first would sort wrongly by id; start times decide.
        val later = night("2026-09-08T19:00:00Z", nine1) { frame(a) }
        night("2026-09-01T19:00:00Z", nine1) { frame(b); frame(b) }
        assertEquals("id1", later) // the later night got the smaller id
        assertEquals(Streak(a, 1), stats().currentStreak)
        assertEquals(mapOf(a to 1, b to 2), stats().longestStreaks)
    }

    @Test
    fun aNightInProgressCountsItsMatchesButNotAsANight() {
        night("2026-09-01T19:00:00Z", nine1, end = false) { frame(a); frame(a) }
        val s = stats()
        assertEquals(Record(2, 0), s.matches)
        assertEquals(NightsRecord(), s.nights)
    }

    @Test
    fun aLevelNightIsADraw() {
        night("2026-09-01T19:00:00Z", nine1) { frame(a); frame(b) }
        assertEquals(NightsRecord(drawn = 1), stats().nights)
    }

    @Test
    fun streakHelpers() {
        assertEquals(Streak(null, 0), StatsCalculator.currentStreak(emptyList()))
        assertEquals(Streak(a, 2), StatsCalculator.currentStreak(listOf(b, a, a)))
        assertEquals(3, StatsCalculator.longestStreak(listOf(a, a, a, b, a), a))
        assertEquals(0, StatsCalculator.longestStreak(listOf(b, b), a))
    }

    @Test
    fun matchesAndFramesOfADeletedSessionAreIgnored() {
        // Listeners can briefly hold a deleted session's matches and frames after the session.
        val id = night("2026-09-01T19:00:00Z", nine1) { frame(a) }
        val match = store.matches(id).single()
        val orphans = StatsCalculator.compute(
            a, b,
            sessions = emptyList(),
            matches = listOf(SessionMatch(id, match)),
            frames = store.frames(id, match.id).map { SessionFrame(id, match.id, it) },
        )
        assertEquals(Record(0, 0), orphans.matches)
        assertEquals(Record(0, 0), orphans.frames)
    }
}
