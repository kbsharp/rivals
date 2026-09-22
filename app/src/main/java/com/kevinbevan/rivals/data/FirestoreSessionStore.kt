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
import com.kevinbevan.rivals.domain.Schema
import com.kevinbevan.rivals.domain.WritePlan
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.displayNames
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Rivals' sessions, in Firestore. Commits aren't awaited (they only complete once the server
 * acknowledges them); Firestore applies them to its local cache at once.
 */
class FirestoreSessionStore(
    private val db: FirebaseFirestore,
    private val players: PlayerRepository,
) : SessionStore {

    private val _writeErrors = MutableSharedFlow<Exception>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val writeErrors: SharedFlow<Exception> = _writeErrors.asSharedFlow()

    // Reads

    /** A cache miss alone isn't proof the session doesn't exist: it may not have downloaded yet. */
    override fun observeSession(sessionId: String): Flow<Synced<Session?>> =
        sessionRef(sessionId).snapshots(MetadataChanges.INCLUDE)
            .filter { it.exists() || !it.metadata.isFromCache }
            .map { snap -> Synced(snap.takeIf { it.exists() }?.toSession(), snap.metadata.hasPendingWrites()) }

    override fun observeMatches(sessionId: String): Flow<Synced<List<Match>>> =
        matchesQuery(sessionId).snapshots(MetadataChanges.INCLUDE).map { snap ->
            Synced(snap.documents.map { it.toMatch() }, snap.metadata.hasPendingWrites())
        }

    override fun observeFrames(sessionId: String, matchId: String): Flow<Synced<List<Frame>>> =
        framesQuery(sessionId, matchId).snapshots(MetadataChanges.INCLUDE).map { snap ->
            Synced(snap.documents.map { it.toFrame() }, snap.metadata.hasPendingWrites())
        }

    override fun observeNames(session: Session): Flow<Map<String, String>> =
        players.observePlayers(session.playerIds).map { displayNames(it) }

    // Loading current state for an action. The cache already holds everything the screen is
    // listening to, including this phone's unsynced writes, so it's tried first; the server is
    // only asked when the cache is missing something.

    override suspend fun loadSession(sessionId: String): Session {
        val snap = getCacheFirst(sessionRef(sessionId))
        check(snap.exists()) { "Session $sessionId doesn't exist" }
        return snap.toSession()
    }

    override suspend fun loadMatches(sessionId: String): List<Match> {
        val query = matchesQuery(sessionId)
        val cached = query.get(Source.CACHE).await()
        val snap = if (cached.isEmpty) query.get().await() else cached
        return snap.documents.map { it.toMatch() }.sortedByDescending { it.number }
    }

    override suspend fun loadFrames(sessionId: String, match: Match): List<Frame> {
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

    override fun commit(plan: WritePlan) {
        db.batchOf(plan).commit().addOnFailureListener { _writeErrors.tryEmit(it) }
    }

    private fun sessionRef(sessionId: String) = db.ref(DocPath.SessionDoc(sessionId))

    private fun matchesQuery(sessionId: String): Query =
        sessionRef(sessionId).collection(Schema.MATCHES).orderBy(Schema.NUMBER)

    private fun framesQuery(sessionId: String, matchId: String): Query =
        db.ref(DocPath.MatchDoc(sessionId, matchId)).collection(Schema.FRAMES).orderBy(Schema.NUMBER)
}

// Pending server timestamps read as the local estimate rather than null.
internal fun DocumentSnapshot.fields(): Map<String, Any?> =
    getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).orEmpty()

internal fun DocumentSnapshot.toSession() = sessionFrom(id, fields())
internal fun DocumentSnapshot.toMatch() = matchFrom(id, fields())
internal fun DocumentSnapshot.toFrame() = frameFrom(id, fields())
