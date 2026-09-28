package com.kevinbevan.rivals.data

import com.kevinbevan.rivals.domain.MatchWithLastFrame
import com.kevinbevan.rivals.domain.RecordOutcome
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Playing a session: its matches and frames, and the actions on them.
 *
 * Reads are [Flow]s from the [store]. Actions read the current state, ask [ScoreRules] for a
 * [com.kevinbevan.rivals.domain.WritePlan], and commit it as one write without waiting for a
 * server: Firestore applies it to its local cache straight away and syncs when it can, so
 * nothing here blocks on the network. Commit failures surface through [writeErrors].
 *
 * The same class runs rivals' sessions (in Firestore) and guest games (on the phone).
 */
class SessionRepository(
    private val store: SessionStore,
    private val rules: ScoreRules,
) {
    // Serialises actions, so a quick double tap plans against the state the first tap left.
    private val mutex = Mutex()

    val writeErrors: SharedFlow<Exception> get() = store.writeErrors

    // Reads

    fun observeSession(sessionId: String): Flow<Synced<Session?>> = store.observeSession(sessionId)

    fun observeMatches(sessionId: String): Flow<Synced<List<Match>>> = store.observeMatches(sessionId)

    fun observeFrames(sessionId: String, matchId: String): Flow<Synced<List<Frame>>> =
        store.observeFrames(sessionId, matchId)

    fun observeNames(session: Session): Flow<Map<String, String>> = store.observeNames(session)

    /** The session's matches in order, each with its frames. For looking back over a session. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMatchesWithFrames(sessionId: String): Flow<Synced<List<MatchWithFrames>>> =
        observeMatches(sessionId).flatMapLatest { matches ->
            if (matches.value.isEmpty()) return@flatMapLatest flowOf(Synced(emptyList(), matches.hasPendingWrites))
            combine(matches.value.map { observeFrames(sessionId, it.id) }) { frames ->
                Synced(
                    matches.value.zip(frames) { match, f -> MatchWithFrames(match, f.value) },
                    matches.hasPendingWrites || frames.any { it.hasPendingWrites },
                )
            }
        }

    // Actions

    /** Records a frame won by [winnerId] in the running match. */
    suspend fun recordFrame(sessionId: String, winnerId: String, recordedBy: String): RecordOutcome =
        mutex.withLock {
            val session = store.loadSession(sessionId)
            val match = loadActiveMatch(sessionId)
                ?: error("There's no match running")
            rules.recordFrame(session, match, winnerId, recordedBy).also { store.commit(it.plan) }
        }

    private suspend fun loadActiveMatch(sessionId: String): Match? {
        // Retry a few times to handle the race between committing a new session and the match
        // appearing in the cache. Each retry includes a tiny delay to let the cache update.
        repeat(10) {
            val match = store.loadMatches(sessionId).firstOrNull()?.takeIf { it.status == Status.ACTIVE }
            if (match != null) return match
            if (it < 9) delay(10)
        }
        return null
    }

    /** Takes back the session's last frame. Returns false if there was nothing to undo. */
    suspend fun undoLastFrame(sessionId: String): Boolean = mutex.withLock {
        val session = store.loadSession(sessionId)
        val (latest, previous) = store.loadMatches(sessionId).let { it.getOrNull(0) to it.getOrNull(1) }
        if (latest == null) return false
        val plan = rules.undoLastFrame(
            session,
            MatchWithLastFrame(latest, loadLastFrame(sessionId, latest)),
            previous?.let { MatchWithLastFrame(it, loadLastFrame(sessionId, it)) },
        ) ?: return false
        store.commit(plan)
        true
    }

    /**
     * Tags or untags [event] on the session's last frame: the latest match's, or the previous
     * match's when the latest is the empty follow-on started after a race was won. Returns
     * false if no frame has been played.
     */
    suspend fun toggleLastFrameEvent(sessionId: String, event: FrameEvent): Boolean = mutex.withLock {
        val session = store.loadSession(sessionId)
        val (match, frame) = store.loadMatches(sessionId).take(2).firstNotNullOfOrNull { m ->
            loadLastFrame(sessionId, m)?.let { m to it }
        } ?: return false
        val events = if (event in frame.events) frame.events - event else frame.events + event
        store.commit(rules.setFrameEvents(session, match, frame, events))
        true
    }

    /** Ends the running match by hand; see [ScoreRules.endMatch]. Returns its winner, if any. */
    suspend fun endMatch(sessionId: String): String? = mutex.withLock {
        val session = store.loadSession(sessionId)
        val match = store.loadMatches(sessionId).firstOrNull()?.takeIf { it.status == Status.ACTIVE }
            ?: return null
        rules.endMatch(session, match).also { store.commit(it.plan) }.winnerId
    }

    /** Starts a match when none is running. */
    suspend fun startMatch(sessionId: String, settings: MatchSettings) = mutex.withLock {
        val (plan, _) = rules.startMatch(
            store.loadSession(sessionId),
            store.loadMatches(sessionId).firstOrNull(),
            settings,
        )
        store.commit(plan)
    }

    /** Changes the running match's settings before its first frame. */
    suspend fun changeSettings(sessionId: String, settings: MatchSettings) = mutex.withLock {
        val match = loadActiveMatch(sessionId)
            ?: error("There's no match running")
        store.commit(rules.changeSettings(store.loadSession(sessionId), match, settings))
    }

    /**
     * Ends the session. Returns true if nothing had been played, so it was deleted instead;
     * see [ScoreRules.endSession].
     */
    suspend fun endSession(sessionId: String, endedBy: String? = null): Boolean = mutex.withLock {
        val session = store.loadSession(sessionId)
        if (session.status == Status.ENDED) return false
        val matches = store.loadMatches(sessionId)
        store.commit(rules.endSession(session, matches, endedBy))
        matches.all { it.framesPlayed == 0 }
    }

    /** Makes [uid]'s phone the one that records racks. */
    suspend fun takeOverScoring(sessionId: String, uid: String): Unit = mutex.withLock {
        val session = store.loadSession(sessionId)
        if (session.scorerId != uid) store.commit(rules.takeOverScoring(session, uid))
    }

    /** Deletes a finished session with all its matches and frames. */
    suspend fun deleteSession(sessionId: String): Unit = mutex.withLock {
        val session = store.loadSession(sessionId)
        val matches = store.loadMatches(sessionId).map { MatchWithFrames(it, store.loadFrames(sessionId, it)) }
        // A batch holds at most 500 writes; a long night's frames could exceed that.
        rules.deleteSession(session, matches).chunked(MAX_BATCH).forEach(store::commit)
    }

    private suspend fun loadLastFrame(sessionId: String, match: Match): Frame? =
        store.loadFrames(sessionId, match).maxWithOrNull(compareBy<Frame> { it.number }.thenBy { it.recordedAt })

    companion object {
        const val MAX_BATCH = 450

        /** Ended sessions, newest first. Sorted here rather than in a query, so no index is needed. */
        fun pastSessions(sessions: List<Session>): List<Session> =
            sessions.filter { it.status == Status.ENDED }
                .sortedWith(compareBy<Session, Instant?>(nullsLast(reverseOrder())) { it.startedAt }.thenBy { it.id })

        /**
         * The live session. Normally there's at most one; if two phones both started one
         * offline, the oldest wins so both phones agree.
         */
        fun oldestActive(sessions: List<Session>): Session? =
            sessions.filter { it.status == Status.ACTIVE }
                .minWithOrNull(compareBy<Session, Instant?>(nullsLast()) { it.startedAt }.thenBy { it.id })

        /** Venues from [sessions], most recent first, to offer when starting a new one. */
        fun recentVenues(sessions: List<Session>): List<String> =
            sessions.sortedByDescending { it.startedAt }
                .mapNotNull { it.venue?.trim()?.takeIf(String::isNotEmpty) }
                .distinctBy { it.lowercase() }
                .take(4)
    }
}
