package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.FrameEvent.BREAK_AND_RUN
import com.kevinbevan.rivals.model.FrameEvent.GOLDEN_BREAK
import com.kevinbevan.rivals.model.FrameEvent.THREE_FOULS
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightsTest {
    private val a = "a"
    private val b = "b"

    private fun match(raceTo: Int?, ended: Boolean, vararg frames: Pair<String, Set<FrameEvent>>): MatchWithFrames {
        val wins = frames.groupingBy { it.first }.eachCount()
        val winner = if (ended) wins.maxByOrNull { it.value }?.key else null
        return MatchWithFrames(
            Match("m", 1, MatchSettings(null, raceTo), if (ended) Status.ENDED else Status.ACTIVE, wins, winner),
            frames.mapIndexed { i, (w, e) -> Frame("f$i", i + 1, w, recordedBy = a, events = e) },
        )
    }

    private fun f(winner: String, vararg events: FrameEvent) = winner to events.toSet()

    @Test
    fun aShutoutIsToldOnceNotAlsoAsARun() {
        val h = highlightsOf(match(3, true, f(a), f(a), f(a)))
        assertEquals(listOf(Highlight.Shutout(a, 3)), h)
    }

    @Test
    fun aComebackToAHillHillWinIsBoth() {
        val h = highlightsOf(match(3, true, f(a), f(a), f(b), f(b), f(b)))
        assertEquals(
            listOf(Highlight.Comeback(b, 0 to 2), Highlight.HillHill(b), Highlight.Run(b, 3)),
            h,
        )
    }

    @Test
    fun oneFrameBehindIsNotAComeback() {
        val h = highlightsOf(match(3, true, f(b), f(a), f(a), f(a)))
        assertEquals(listOf(Highlight.Run(a, 3)), h)
    }

    @Test
    fun anUnfinishedMatchOnlyHasRunsAndTags() {
        val h = highlightsOf(match(5, false, f(a), f(a), f(a, GOLDEN_BREAK), f(b)))
        assertEquals(listOf(Highlight.Run(a, 3), Highlight.Tagged(a, GOLDEN_BREAK, 3)), h)
    }

    @Test
    fun consecutiveBreakAndRunsByOnePlayerAreAPack() {
        val h = highlightsOf(
            match(
                null, false,
                f(a, BREAK_AND_RUN), f(a, BREAK_AND_RUN, GOLDEN_BREAK), f(b, BREAK_AND_RUN), f(a, BREAK_AND_RUN),
            ),
        )
        assertEquals(
            listOf(
                Highlight.Pack(a, 1, 2),
                Highlight.Tagged(a, GOLDEN_BREAK, 2),
                Highlight.Tagged(b, BREAK_AND_RUN, 3),
                Highlight.Tagged(a, BREAK_AND_RUN, 4),
            ),
            h,
        )
    }

    @Test
    fun totalsCountEachPlayersTagsAndSkipEventsNobodyHad() {
        val m = match(null, false, f(a, BREAK_AND_RUN), f(b, THREE_FOULS), f(a, BREAK_AND_RUN))
        assertEquals(
            mapOf(BREAK_AND_RUN to Count(2, 0), THREE_FOULS to Count(0, 1)),
            eventTotals(listOf(m), a),
        )
    }
}
