package com.kevinbevan.rivals.model

import java.time.Instant

/** How a match is played; carried over when the next match starts automatically. */
data class MatchSettings(
    val gameType: GameType,
    /** First to this many frames wins. `null` means open-ended (ended by hand). */
    val raceTo: Int?,
) {
    init {
        require(raceTo == null || raceTo >= 1) { "raceTo must be at least 1, was $raceTo" }
    }
}

/** A race to N frames. Mirrors `sessions/{sessionId}/matches/{matchId}`. */
data class Match(
    val id: String,
    /** 1-based position within the session; orders matches without relying on server timestamps. */
    val number: Int,
    val settings: MatchSettings,
    val status: Status,
    /** Denormalised count of frames won, keyed by uid. */
    val frameWins: Map<String, Int>,
    val winnerId: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
) {
    val framesPlayed: Int get() = frameWins.values.sum()
}

/** A single rack. Mirrors `sessions/{sessionId}/matches/{matchId}/frames/{frameId}`. */
data class Frame(
    val id: String,
    /** 1-based position within the match. */
    val number: Int,
    val winnerId: String,
    val breakerId: String? = null,
    val recordedBy: String,
    val recordedAt: Instant? = null,
)

/** Wins for [uid] in a tally map, treating a missing key as zero. */
fun Map<String, Int>.winsOf(uid: String): Int = this[uid] ?: 0

/** A match with all its frames, in order. */
data class MatchWithFrames(val match: Match, val frames: List<Frame>)
