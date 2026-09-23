package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf

/** Something worth remembering about a match, credited to [playerId]. */
sealed interface Highlight {
    val playerId: String

    /** Won the match without the other player taking a frame. */
    data class Shutout(override val playerId: String, val frames: Int) : Highlight

    /** Won the match from [trailedBy] (their score first) at its worst. */
    data class Comeback(override val playerId: String, val trailedBy: Pair<Int, Int>) : Highlight

    /** Won a race that went to the last frame, both on the hill. */
    data class HillHill(override val playerId: String) : Highlight

    /** The match's longest run of frames in a row, when it's at least [MIN_RUN]. */
    data class Run(override val playerId: String, val frames: Int) : Highlight

    /** A frame tagged with [event]. */
    data class Tagged(override val playerId: String, val event: FrameEvent, val frame: Int) : Highlight

    /** Break and runs in consecutive frames, from [firstFrame] to [lastFrame]. */
    data class Pack(override val playerId: String, val firstFrame: Int, val lastFrame: Int) : Highlight {
        val size: Int get() = lastFrame - firstFrame + 1
    }

    companion object {
        const val MIN_RUN = 3
        const val MIN_COMEBACK = 2
    }
}

/**
 * The match's story, then its frame-by-frame moments. Only a finished match with a winner can
 * be a shutout, comeback or hill-hill; runs and tags show while it's still being played.
 * A run that is the whole match is left to the shutout, and consecutive break and runs by one
 * player are told as a pack rather than one by one.
 */
fun highlightsOf(item: MatchWithFrames): List<Highlight> {
    val match = item.match
    val frames = item.frames.sortedBy { it.number }
    val out = mutableListOf<Highlight>()

    val winner = match.winnerId.takeIf { match.status == Status.ENDED }
    val loser = winner?.let { w -> frames.map { it.winnerId }.firstOrNull { it != w } }
    var shutout = false
    if (winner != null) {
        val won = match.frameWins.winsOf(winner)
        val lost = loser?.let { match.frameWins.winsOf(it) } ?: 0
        if (lost == 0 && won > 1) {
            shutout = true
            out += Highlight.Shutout(winner, won)
        } else {
            var mine = 0
            var theirs = 0
            var worst = 0 to 0
            frames.forEach { f ->
                if (f.winnerId == winner) mine++ else theirs++
                if (theirs - mine > worst.second - worst.first) worst = mine to theirs
            }
            if (worst.second - worst.first >= Highlight.MIN_COMEBACK) out += Highlight.Comeback(winner, worst)
            val race = match.settings.raceTo
            if (race != null && race > 1 && won == race && lost == race - 1) out += Highlight.HillHill(winner)
        }
    }

    if (!shutout) {
        var best: Highlight.Run? = null
        var i = 0
        while (i < frames.size) {
            var j = i
            while (j + 1 < frames.size && frames[j + 1].winnerId == frames[i].winnerId) j++
            val length = j - i + 1
            if (length >= Highlight.MIN_RUN && length > (best?.frames ?: 0)) {
                best = Highlight.Run(frames[i].winnerId, length)
            }
            i = j + 1
        }
        best?.let { out += it }
    }

    var k = 0
    while (k < frames.size) {
        val frame = frames[k]
        if (FrameEvent.BREAK_AND_RUN in frame.events) {
            var end = k
            while (end + 1 < frames.size &&
                frames[end + 1].winnerId == frame.winnerId &&
                FrameEvent.BREAK_AND_RUN in frames[end + 1].events
            ) end++
            if (end > k) {
                out += Highlight.Pack(frame.winnerId, frame.number, frames[end].number)
                (k..end).forEach { idx ->
                    frames[idx].events.filter { it != FrameEvent.BREAK_AND_RUN }.sortedBy { it.ordinal }
                        .forEach { out += Highlight.Tagged(frames[idx].winnerId, it, frames[idx].number) }
                }
                k = end + 1
                continue
            }
        }
        frame.events.sortedBy { it.ordinal }.forEach { out += Highlight.Tagged(frame.winnerId, it, frame.number) }
        k++
    }
    return out
}

/** How many of each tagged event each player had across [matches], leaving out events nobody had. */
fun eventTotals(matches: List<MatchWithFrames>, myId: String): Map<FrameEvent, Count> {
    val frames = matches.flatMap { it.frames }
    return FrameEvent.entries.associateWith { event ->
        val tagged = frames.filter { event in it.events }
        Count(mine = tagged.count { it.winnerId == myId }, theirs = tagged.count { it.winnerId != myId })
    }.filterValues { it.mine + it.theirs > 0 }
}
