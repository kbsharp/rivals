package com.kevinbevan.rivals.data

import com.kevinbevan.rivals.domain.WritePlan
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/** A value read from a store, and whether it includes local writes the server hasn't confirmed. */
data class Synced<T>(val value: T, val hasPendingWrites: Boolean)

/**
 * Where sessions live: Firestore for rivals ([FirestoreSessionStore]), or the phone for guest
 * games ([LocalSessionStore]). [SessionRepository] plays a night the same way against either.
 */
interface SessionStore {
    /** The session, or `null` once it's known not to exist. */
    fun observeSession(sessionId: String): Flow<Synced<Session?>>

    /** The session's matches, in order. */
    fun observeMatches(sessionId: String): Flow<Synced<List<Match>>>

    /** A match's frames, in order. */
    fun observeFrames(sessionId: String, matchId: String): Flow<Synced<List<Frame>>>

    /** Names to show for [session]'s players, keyed by player id. */
    fun observeNames(session: Session): Flow<Map<String, String>>

    /** The session as it stands, for planning an action. */
    suspend fun loadSession(sessionId: String): Session

    /** The session's matches, latest first. */
    suspend fun loadMatches(sessionId: String): List<Match>

    /** All of [match]'s frames. */
    suspend fun loadFrames(sessionId: String, match: Match): List<Frame>

    /** Applies [plan] as one atomic write, without waiting for a server. Failures go to [writeErrors]. */
    fun commit(plan: WritePlan)

    val writeErrors: SharedFlow<Exception>
}
