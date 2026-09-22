package com.kevinbevan.rivals.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.kevinbevan.rivals.domain.DocPath
import com.kevinbevan.rivals.domain.MatchWithLastFrame
import com.kevinbevan.rivals.domain.RecordOutcome
import com.kevinbevan.rivals.domain.Schema
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.domain.WritePlan
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** A value read from Firestore, and whether it includes local writes the server hasn't confirmed. */
data class Synced<T>(val value: T, val hasPendingWrites: Boolean)

/**
 * Sessions, with their matches and frames.
 *
 * Reads are snapshot-listener [Flow]s. Actions read the current state, ask [ScoreRules] for a
 * [WritePlan], and commit it as one batch without waiting for the server: Firestore applies
 * the batch to its local cache straight away and syncs it when it can, so nothing here blocks
 * on the network. Commit failures surface through [writeErrors].
 */
class SessionRepository(
    private val db: FirebaseFirestore,
    private val rules: ScoreRules,
) {
    // Serialises actions, so a quick double tap plans against the state the first tap left.
    private val mutex = Mutex()

    private val _writeErrors = MutableSharedFlow<Exception>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val writeErrors: SharedFlow<Exception> = _writeErrors.asSharedFlow()

    private val sessions = db.collection(Schema.SESSIONS)

    // Reads

    /** Every session. There are only two players, so this stays small. */
    fun observeSessions(): Flow<Synced<List<Session>>> =
        sessions.snapshots(MetadataChanges.INCLUDE).map { snap ->
            Synced(snap.documents.map { it.toSession() }, snap.metadata.hasPendingWrites())
        }

    /**
     * The session, or `null` once the server confirms it doesn't exist. (A cache miss alone
     * isn't proof: the doc may just not have been downloaded yet.)
     */
    fun observeSession(sessionId: String): Flow<Synced<Session?>> =
        sessionRef(sessionId).snapshots(MetadataChanges.INCLUDE)
            .filter { it.exists() || !it.metadata.isFromCache }
            .map { snap -> Synced(snap.takeIf { it.exists() }?.toSession(), snap.metadata.hasPendingWrites()) }

    /** The session's matches, in order. */
    fun observeMatches(sessionId: String): Flow<Synced<List<Match>>> =
        matchesQuery(sessionId).snapshots(MetadataChanges.INCLUDE).map { snap ->
            Synced(snap.documents.map { it.toMatch() }, snap.metadata.hasPendingWrites())
        }

    /** A match's frames, in order. */
    fun observeFrames(sessionId: String, matchId: String): Flow<Synced<List<Frame>>> =
        framesQuery(sessionId, matchId).snapshots(MetadataChanges.INCLUDE).map { snap ->
            Synced(snap.documents.map { it.toFrame() }, snap.metadata.hasPendingWrites())
        }

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

    /**
     * Starts a session against [rivalId] and returns its id. If a session is already active
     * (say the other phone just started one), returns that one instead.
     *
     * This check isn't a transaction, because transactions need the network and a session
     * has to be startable in the pool hall with no signal. Two phones starting a session
     * offline at the same moment could both succeed; readers then pick the oldest.
     */
    suspend fun startSession(
        myId: String,
        rivalId: String,
        settings: MatchSettings,
        venue: String? = null,
    ): String =
        mutex.withLock {
            val active = sessions.whereEqualTo(Schema.STATUS, Status.ACTIVE.wire).get().await()
                .documents.map { it.toSession() }
            oldestActive(active)?.let { return it.id }
            val outcome = rules.startSession(listOf(myId, rivalId), createdBy = myId, settings, venue)
            commit(outcome.plan)
            outcome.sessionId
        }

    /** Records a frame won by [winnerId] in the running match, broken by [breakerId] if known. */
    suspend fun recordFrame(
        sessionId: String,
        winnerId: String,
        recordedBy: String,
        breakerId: String? = null,
    ): RecordOutcome =
        mutex.withLock {
            val session = loadSession(sessionId)
            val match = loadMatches(sessionId).firstOrNull()?.takeIf { it.status == Status.ACTIVE }
                ?: error("There's no match running")
            rules.recordFrame(session, match, winnerId, recordedBy, breakerId).also { commit(it.plan) }
        }

    /** Takes back the session's last frame. Returns false if there was nothing to undo. */
    suspend fun undoLastFrame(sessionId: String): Boolean = mutex.withLock {
        val session = loadSession(sessionId)
        val (latest, previous) = loadMatches(sessionId).let { it.getOrNull(0) to it.getOrNull(1) }
        if (latest == null) return false
        val plan = rules.undoLastFrame(
            session,
            MatchWithLastFrame(latest, loadLastFrame(sessionId, latest)),
            previous?.let { MatchWithLastFrame(it, loadLastFrame(sessionId, it)) },
        ) ?: return false
        commit(plan)
        true
    }

    /** Ends the running match by hand; see [ScoreRules.endMatch]. Returns its winner, if any. */
    suspend fun endMatch(sessionId: String): String? = mutex.withLock {
        val session = loadSession(sessionId)
        val match = loadMatches(sessionId).firstOrNull()?.takeIf { it.status == Status.ACTIVE }
            ?: return null
        rules.endMatch(session, match).also { commit(it.plan) }.winnerId
    }

    /** Starts a match when none is running. */
    suspend fun startMatch(sessionId: String, settings: MatchSettings) = mutex.withLock {
        val (plan, _) = rules.startMatch(loadSession(sessionId), loadMatches(sessionId).firstOrNull(), settings)
        commit(plan)
    }

    /** Changes the running match's settings before its first frame. */
    suspend fun changeSettings(sessionId: String, settings: MatchSettings) = mutex.withLock {
        val match = loadMatches(sessionId).firstOrNull()?.takeIf { it.status == Status.ACTIVE }
            ?: error("There's no match running")
        commit(rules.changeSettings(loadSession(sessionId), match, settings))
    }

    /**
     * Ends the session. Returns true if nothing had been played, so it was deleted instead;
     * see [ScoreRules.endSession].
     */
    suspend fun endSession(sessionId: String): Boolean = mutex.withLock {
        val session = loadSession(sessionId)
        if (session.status == Status.ENDED) return false
        val matches = loadMatches(sessionId)
        commit(rules.endSession(session, matches))
        matches.all { it.framesPlayed == 0 }
    }

    /** Deletes a finished session with all its matches and frames. */
    suspend fun deleteSession(sessionId: String): Unit = mutex.withLock {
        val session = loadSession(sessionId)
        val matches = loadMatches(sessionId).map { MatchWithFrames(it, loadFrames(sessionId, it)) }
        // A batch holds at most 500 writes; a long night's frames could exceed that.
        rules.deleteSession(session, matches).chunked(MAX_BATCH).forEach(::commit)
    }

    // Loading current state for an action. The cache already holds everything the screen is
    // listening to, including this phone's unsynced writes, so it's tried first; the server is
    // only asked when the cache is missing something.

    private suspend fun loadSession(sessionId: String): Session {
        val snap = getCacheFirst(sessionRef(sessionId))
        check(snap.exists()) { "Session $sessionId doesn't exist" }
        return snap.toSession()
    }

    /** The session's matches, latest first. */
    private suspend fun loadMatches(sessionId: String): List<Match> {
        val query = matchesQuery(sessionId)
        val cached = query.get(Source.CACHE).await()
        val snap = if (cached.isEmpty) query.get().await() else cached
        return snap.documents.map { it.toMatch() }.sortedByDescending { it.number }
    }

    private suspend fun loadLastFrame(sessionId: String, match: Match): Frame? =
        loadFrames(sessionId, match).maxWithOrNull(compareBy<Frame> { it.number }.thenBy { it.recordedAt })

    private suspend fun loadFrames(sessionId: String, match: Match): List<Frame> {
        if (match.framesPlayed == 0) return emptyList()
        val query = framesQuery(sessionId, match.id)
        var snap: QuerySnapshot = query.get(Source.CACHE).await()
        // Fewer cached frames than the tally says means some were never downloaded.
        if (snap.size() < match.framesPlayed) snap = query.get().await()
        return snap.documents.map { it.toFrame() }
    }

    private suspend fun getCacheFirst(ref: DocumentReference): DocumentSnapshot = try {
        ref.get(Source.CACHE).await()
    } catch (_: FirebaseFirestoreException) {
        ref.get().await()
    }

    private fun commit(plan: WritePlan) {
        db.batchOf(plan).commit().addOnFailureListener { _writeErrors.tryEmit(it) }
    }

    private fun sessionRef(sessionId: String) = db.ref(DocPath.SessionDoc(sessionId))

    private fun matchesQuery(sessionId: String): Query =
        sessionRef(sessionId).collection(Schema.MATCHES).orderBy(Schema.NUMBER)

    private fun framesQuery(sessionId: String, matchId: String): Query =
        db.ref(DocPath.MatchDoc(sessionId, matchId)).collection(Schema.FRAMES).orderBy(Schema.NUMBER)

    companion object {
        private const val MAX_BATCH = 450

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
    }
}

// Pending server timestamps read as the local estimate rather than null.
private fun DocumentSnapshot.fields(): Map<String, Any?> =
    getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()

private fun DocumentSnapshot.toSession() = sessionFrom(id, fields())
private fun DocumentSnapshot.toMatch() = matchFrom(id, fields())
private fun DocumentSnapshot.toFrame() = frameFrom(id, fields())
