package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf

/** A match, with the session it belongs to. */
data class SessionMatch(val sessionId: String, val match: Match)

/** A frame, with the session and match it belongs to. */
data class SessionFrame(val sessionId: String, val matchId: String, val frame: Frame)

/** Won and lost, from one player's side. */
data class Record(val won: Int = 0, val lost: Int = 0) {
    val played: Int get() = won + lost
    /** `null` when nothing has been played. */
    val winRate: Double? get() = if (played == 0) null else won.toDouble() / played
}

data class NightsRecord(val won: Int = 0, val lost: Int = 0, val drawn: Int = 0)

/** A run of consecutive match wins. [playerId] is `null` when there's no run (no matches yet). */
data class Streak(val playerId: String?, val length: Int)

/** How many times each side did something, e.g. a break and run. */
data class Count(val mine: Int = 0, val theirs: Int = 0)

data class GameTypeStats(val matches: Record, val frames: Record)

/** Everything on the Stats screen, from [Stats.myId]'s side. */
data class Stats(
    val myId: String,
    val rivalId: String,
    /** Decided matches only: a match ended by hand with no winner counts for neither. */
    val matches: Record,
    val frames: Record,
    val nights: NightsRecord,
    /** Only game types that have been played, in [GameType] order. */
    val byGameType: Map<GameType, GameTypeStats>,
    val currentStreak: Streak,
    /** Each player's longest run of match wins. */
    val longestStreaks: Map<String, Int>,
    /** Tagged frame events, credited to the frame winner. Every [FrameEvent] has an entry. */
    val specials: Map<FrameEvent, Count>,
)

/**
 * Works out [Stats] from every session, match and frame. Pure, so it's unit-tested without
 * Firebase; the screen recomputes it whenever any of them change.
 */
object StatsCalculator {

    fun compute(
        myId: String,
        rivalId: String,
        sessions: List<Session>,
        matches: List<SessionMatch>,
        frames: List<SessionFrame>,
    ): Stats {
        val sessionIds = sessions.map { it.id }.toSet()
        // Oldest first: by when the session started, then match order within it.
        val sessionOrder = sessions
            .sortedWith(compareBy<Session, java.time.Instant?>(nullsLast()) { it.startedAt }.thenBy { it.id })
            .withIndex().associate { (i, s) -> s.id to i }
        val allMatches = matches.filter { it.sessionId in sessionIds }
        val decided = allMatches
            .filter { it.match.status == Status.ENDED && it.match.winnerId != null }
            .sortedWith(compareBy({ sessionOrder[it.sessionId] }, { it.match.number }))
        val matchById = allMatches.associateBy { it.sessionId to it.match.id }
        val knownFrames = frames.filter { (it.sessionId to it.matchId) in matchById }

        fun matchRecord(ms: List<SessionMatch>) = Record(
            won = ms.count { it.match.winnerId == myId },
            lost = ms.count { it.match.winnerId == rivalId },
        )
        fun frameRecord(fs: List<SessionFrame>) = Record(
            won = fs.count { it.frame.winnerId == myId },
            lost = fs.count { it.frame.winnerId == rivalId },
        )

        val byGameType = GameType.entries.mapNotNull { type ->
            val typeMatches = decided.filter { it.match.settings.gameType == type }
            val typeFrames = knownFrames.filter {
                matchById.getValue(it.sessionId to it.matchId).match.settings.gameType == type
            }
            if (typeMatches.isEmpty() && typeFrames.isEmpty()) null
            else type to GameTypeStats(matchRecord(typeMatches), frameRecord(typeFrames))
        }.toMap()

        val nights = sessions.filter { it.status == Status.ENDED }.fold(NightsRecord()) { acc, s ->
            val mine = s.matchWins.winsOf(myId)
            val theirs = s.matchWins.winsOf(rivalId)
            when {
                mine > theirs -> acc.copy(won = acc.won + 1)
                theirs > mine -> acc.copy(lost = acc.lost + 1)
                else -> acc.copy(drawn = acc.drawn + 1)
            }
        }

        val winners = decided.mapNotNull { it.match.winnerId }
        return Stats(
            myId = myId,
            rivalId = rivalId,
            matches = matchRecord(decided),
            frames = frameRecord(knownFrames),
            nights = nights,
            byGameType = byGameType,
            currentStreak = currentStreak(winners),
            longestStreaks = listOf(myId, rivalId).associateWith { longestStreak(winners, it) },
            specials = FrameEvent.entries.associateWith { event ->
                val tagged = knownFrames.filter { event in it.frame.events }
                Count(tagged.count { it.frame.winnerId == myId }, tagged.count { it.frame.winnerId == rivalId })
            },
        )
    }

    /** The run of wins at the end of [winners] (oldest first). */
    fun currentStreak(winners: List<String>): Streak {
        val last = winners.lastOrNull() ?: return Streak(null, 0)
        return Streak(last, winners.takeLastWhile { it == last }.size)
    }

    fun longestStreak(winners: List<String>, playerId: String): Int {
        var best = 0
        var run = 0
        for (w in winners) {
            run = if (w == playerId) run + 1 else 0
            best = maxOf(best, run)
        }
        return best
    }
}
