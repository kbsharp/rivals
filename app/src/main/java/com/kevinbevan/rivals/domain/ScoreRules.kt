package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.domain.DocPath.FrameDoc
import com.kevinbevan.rivals.domain.DocPath.MatchDoc
import com.kevinbevan.rivals.domain.DocPath.SessionDoc
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf

/** Result of recording a frame: the writes to commit, and what they did to the match. */
data class RecordOutcome(
    val plan: WritePlan,
    val matchEnded: Boolean,
    /** Set when [matchEnded]; the player who reached the race. */
    val matchWinnerId: String?,
    /** Id of the match that was started automatically, when [matchEnded]. */
    val nextMatchId: String?,
)

/** A match together with its most recent frame (`null` if it has none). Input to undo. */
data class MatchWithLastFrame(val match: Match, val lastFrame: Frame?)

/**
 * The scoring rules, as pure functions that produce [WritePlan]s.
 *
 * Tallies are written as increments rather than absolute values, so two phones recording
 * at once (or offline) merge instead of overwriting each other. New document ids come from
 * [newId] so tests can make them deterministic.
 */
class ScoreRules(private val newId: () -> String) {

    /**
     * Creates an active session between two players, with its first match already running.
     * A rivals' session carries its [rivalryId]; a guest game has none but has typed-in [names].
     */
    fun startSession(
        playerIds: List<String>,
        createdBy: String,
        settings: MatchSettings,
        venue: String? = null,
        rivalryId: String? = null,
        names: Map<String, String> = emptyMap(),
    ): StartOutcome {
        require(playerIds.size == 2 && playerIds.distinct().size == 2) {
            "A session needs exactly two different players, got $playerIds"
        }
        require(createdBy in playerIds) { "createdBy must be one of the players" }
        val sessionId = newId()
        val matchId = newId()
        val plan = listOf(
            Write.Set(
                SessionDoc(sessionId),
                buildMap {
                    put(Schema.PLAYER_IDS, playerIds)
                    put(Schema.STATUS, Status.ACTIVE.wire)
                    put(Schema.STARTED_AT, FieldOp.ServerTimestamp)
                    put(Schema.CREATED_BY, createdBy)
                    put(Schema.MATCH_WINS, zeroTally(playerIds))
                    if (!venue.isNullOrBlank()) put(Schema.VENUE, venue.trim())
                    if (rivalryId != null) put(Schema.RIVALRY_ID, rivalryId)
                    if (names.isNotEmpty()) put(Schema.NAMES, names)
                },
            ),
            newMatch(sessionId, matchId, number = 1, settings, playerIds),
        )
        return StartOutcome(plan, sessionId, matchId)
    }

    data class StartOutcome(val plan: WritePlan, val sessionId: String, val matchId: String)

    /**
     * Records a frame won by [winnerId]. If that takes them to the race, the same plan ends the
     * match, bumps the session tally and starts the next match with the same settings.
     */
    fun recordFrame(
        session: Session,
        match: Match,
        winnerId: String,
        recordedBy: String,
        breakerId: String? = null,
    ): RecordOutcome {
        checkActive(session, match)
        require(winnerId in session.playerIds) { "Winner $winnerId isn't in this session" }
        require(recordedBy in session.playerIds) { "Recorder $recordedBy isn't in this session" }
        require(breakerId == null || breakerId in session.playerIds) {
            "Breaker $breakerId isn't in this session"
        }

        val frameId = newId()
        val plan = mutableListOf<Write>(
            Write.Set(
                FrameDoc(session.id, match.id, frameId),
                buildMap {
                    put(Schema.NUMBER, match.framesPlayed + 1)
                    put(Schema.WINNER_ID, winnerId)
                    if (breakerId != null) put(Schema.BREAKER_ID, breakerId)
                    put(Schema.RECORDED_BY, recordedBy)
                    put(Schema.RECORDED_AT, FieldOp.ServerTimestamp)
                    put(Schema.PLAYER_IDS, session.playerIds)
                },
            ),
        )

        val raceTo = match.settings.raceTo
        val ended = raceTo != null && match.frameWins.winsOf(winnerId) + 1 >= raceTo
        val matchUpdate = mutableMapOf<String, Any?>(
            Schema.frameWinsOf(winnerId) to FieldOp.Increment(1),
        )
        if (!ended) {
            plan += Write.Update(MatchDoc(session.id, match.id), matchUpdate)
            return RecordOutcome(plan, matchEnded = false, matchWinnerId = null, nextMatchId = null)
        }

        matchUpdate += endMatchFields(winnerId)
        plan += Write.Update(MatchDoc(session.id, match.id), matchUpdate)
        plan += Write.Update(
            SessionDoc(session.id),
            mapOf(Schema.matchWinsOf(winnerId) to FieldOp.Increment(1)),
        )
        val nextId = newId()
        plan += newMatch(session.id, nextId, match.number + 1, match.settings, session.playerIds)
        return RecordOutcome(plan, matchEnded = true, matchWinnerId = winnerId, nextMatchId = nextId)
    }

    /**
     * Takes back the most recent frame of the session.
     *
     * [latest] is the session's highest-numbered match and [previous] the one before it.
     * If [latest] has frames, its last frame is removed (reopening it if it had ended).
     * If [latest] is empty, i.e. it was just started after the previous match ended, it is
     * deleted and the previous match's last frame is taken back, reopening that match and
     * reversing its session tally.
     *
     * Returns `null` when there's nothing to undo.
     */
    fun undoLastFrame(
        session: Session,
        latest: MatchWithLastFrame,
        previous: MatchWithLastFrame?,
    ): WritePlan? {
        check(session.status == Status.ACTIVE) { "Session ${session.id} has ended" }
        if (latest.lastFrame != null) return removeFrame(session, latest.match, latest.lastFrame)
        if (previous?.lastFrame == null) return null
        require(previous.match.number < latest.match.number) { "previous must precede latest" }
        return listOf(Write.Delete(MatchDoc(session.id, latest.match.id))) +
            removeFrame(session, previous.match, previous.lastFrame)
    }

    /**
     * Ends [match] by hand, optionally starting another with [next] settings in the same plan.
     *
     * An open-ended match goes to whoever is ahead (no winner if level). A race that's
     * abandoned before anyone reaches it has no winner. Only a match with a winner counts
     * towards the session tally.
     */
    fun endMatch(session: Session, match: Match, next: MatchSettings? = null): EndMatchOutcome {
        checkActive(session, match)
        val winnerId = manualWinner(match)
        val plan = mutableListOf<Write>(
            Write.Update(MatchDoc(session.id, match.id), endMatchFields(winnerId)),
        )
        if (winnerId != null) {
            plan += Write.Update(
                SessionDoc(session.id),
                mapOf(Schema.matchWinsOf(winnerId) to FieldOp.Increment(1)),
            )
        }
        var nextId: String? = null
        if (next != null) {
            nextId = newId()
            plan += newMatch(session.id, nextId, match.number + 1, next, session.playerIds)
        }
        return EndMatchOutcome(plan, winnerId, nextId)
    }

    data class EndMatchOutcome(val plan: WritePlan, val winnerId: String?, val nextMatchId: String?)

    /** Starts a new match when the session has none running (e.g. after [endMatch] with no next). */
    fun startMatch(session: Session, latest: Match?, settings: MatchSettings): Pair<WritePlan, String> {
        check(session.status == Status.ACTIVE) { "Session ${session.id} has ended" }
        check(latest == null || latest.status == Status.ENDED) { "Match ${latest?.id} is still running" }
        val id = newId()
        val number = (latest?.number ?: 0) + 1
        return listOf(newMatch(session.id, id, number, settings, session.playerIds)) to id
    }

    /**
     * Replaces the tagged [events] on [frame] of [match], e.g. marking it a break and run.
     * Works on an ended match too, since the winning frame is often the one worth tagging.
     */
    fun setFrameEvents(session: Session, match: Match, frame: Frame, events: Set<FrameEvent>): WritePlan {
        check(session.status == Status.ACTIVE) { "Session ${session.id} has ended" }
        return listOf(
            Write.Update(
                FrameDoc(session.id, match.id, frame.id),
                mapOf(Schema.EVENTS to events.sortedBy { it.ordinal }.map { it.wire }),
            ),
        )
    }

    /** Changes how [match] is played, e.g. switching game type. Only allowed before its first frame. */
    fun changeSettings(session: Session, match: Match, settings: MatchSettings): WritePlan {
        checkActive(session, match)
        check(match.framesPlayed == 0) { "Match ${match.id} is under way; end it instead" }
        return listOf(
            Write.Update(
                MatchDoc(session.id, match.id),
                mapOf(Schema.GAME_TYPE to settings.gameType.wire, Schema.RACE_TO to settings.raceTo),
            ),
        )
    }

    /**
     * Ends the session. A running match with no frames is deleted, since it was only ever the
     * automatic follow-on; one with frames is ended by hand as in [endMatch].
     *
     * If no frame was played all night, the session is deleted outright rather than leaving a
     * 0–0 night in the history.
     *
     * @param matches every match in the session.
     */
    fun endSession(session: Session, matches: List<Match>): WritePlan {
        check(session.status == Status.ACTIVE) { "Session ${session.id} has already ended" }
        if (matches.all { it.framesPlayed == 0 }) {
            return matches.map { Write.Delete(MatchDoc(session.id, it.id)) } +
                Write.Delete(SessionDoc(session.id))
        }
        val plan = mutableListOf<Write>()
        val activeMatch = matches.filter { it.status == Status.ACTIVE }.maxByOrNull { it.number }
        if (activeMatch != null) {
            plan += if (activeMatch.framesPlayed == 0) {
                listOf(Write.Delete(MatchDoc(session.id, activeMatch.id)))
            } else {
                endMatch(session, activeMatch).plan
            }
        }
        plan += Write.Update(
            SessionDoc(session.id),
            mapOf(
                Schema.STATUS to Status.ENDED.wire,
                Schema.ENDED_AT to FieldOp.ServerTimestamp,
            ),
        )
        return plan
    }

    /**
     * Deletes a finished session and everything in it, e.g. a night recorded by mistake.
     * Frames go first and the session last, so if a large delete is split across batches,
     * a partial failure never leaves frames behind an already-deleted session.
     */
    fun deleteSession(session: Session, matches: List<MatchWithFrames>): WritePlan {
        check(session.status == Status.ENDED) { "End session ${session.id} before deleting it" }
        return matches.flatMap { m -> m.frames.map { Write.Delete(FrameDoc(session.id, m.match.id, it.id)) } } +
            matches.map { Write.Delete(MatchDoc(session.id, it.match.id)) } +
            Write.Delete(SessionDoc(session.id))
    }

    private fun removeFrame(session: Session, match: Match, frame: Frame): WritePlan {
        check(match.frameWins.winsOf(frame.winnerId) > 0) {
            "Frame ${frame.id} isn't reflected in match ${match.id}'s tally"
        }
        val reopen = match.status == Status.ENDED
        val matchUpdate = buildMap<String, Any?> {
            put(Schema.frameWinsOf(frame.winnerId), FieldOp.Increment(-1))
            if (reopen) {
                put(Schema.STATUS, Status.ACTIVE.wire)
                put(Schema.WINNER_ID, FieldOp.Delete)
                put(Schema.ENDED_AT, FieldOp.Delete)
            }
        }
        return buildList {
            add(Write.Delete(FrameDoc(session.id, match.id, frame.id)))
            add(Write.Update(MatchDoc(session.id, match.id), matchUpdate))
            if (reopen && match.winnerId != null) {
                add(
                    Write.Update(
                        SessionDoc(session.id),
                        mapOf(Schema.matchWinsOf(match.winnerId) to FieldOp.Increment(-1)),
                    ),
                )
            }
        }
    }

    private fun newMatch(
        sessionId: String,
        matchId: String,
        number: Int,
        settings: MatchSettings,
        playerIds: List<String>,
    ) = Write.Set(
        MatchDoc(sessionId, matchId),
        mapOf(
            Schema.NUMBER to number,
            Schema.GAME_TYPE to settings.gameType.wire,
            Schema.RACE_TO to settings.raceTo,
            Schema.STATUS to Status.ACTIVE.wire,
            Schema.FRAME_WINS to zeroTally(playerIds),
            Schema.STARTED_AT to FieldOp.ServerTimestamp,
            // Copied from the session so stats can query every match a player is in (the
            // rules can only allow a collection-group query on the document's own fields).
            Schema.PLAYER_IDS to playerIds,
        ),
    )

    private fun endMatchFields(winnerId: String?): Map<String, Any?> = buildMap {
        put(Schema.STATUS, Status.ENDED.wire)
        put(Schema.ENDED_AT, FieldOp.ServerTimestamp)
        if (winnerId != null) put(Schema.WINNER_ID, winnerId)
    }

    private fun checkActive(session: Session, match: Match) {
        check(session.status == Status.ACTIVE) { "Session ${session.id} has ended" }
        check(match.status == Status.ACTIVE) { "Match ${match.id} has ended" }
    }

    companion object {
        /** Who takes an unfinished match that's ended by hand; see [endMatch]. */
        fun manualWinner(match: Match): String? {
            if (match.settings.raceTo != null) return null
            val (leader, second) = match.frameWins.entries.sortedByDescending { it.value }
                .let { it.getOrNull(0) to it.getOrNull(1) }
            if (leader == null || leader.value == 0) return null
            return leader.key.takeIf { second == null || leader.value > second.value }
        }

        /**
         * Who breaks next, assuming players alternate: whoever didn't break the last frame
         * that has a breaker recorded. `null` if there's nothing to go on yet.
         */
        fun alternateBreaker(lastBreakerId: String?, playerIds: List<String>): String? =
            lastBreakerId?.let { last -> playerIds.firstOrNull { it != last } }

        /** All-time match wins per player, summed over every session (active ones included). */
        fun headToHead(sessions: List<Session>): Map<String, Int> =
            sessions.flatMap { it.matchWins.entries }
                .groupingBy { it.key }
                .fold(0) { total, entry -> total + entry.value }

        private fun zeroTally(playerIds: List<String>) = playerIds.associateWith { 0 }
    }
}
