package com.kevinbevan.rivals.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import com.kevinbevan.rivals.domain.Docs
import com.kevinbevan.rivals.domain.GuestClaim
import com.kevinbevan.rivals.domain.Schema
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.domain.SessionFrame
import com.kevinbevan.rivals.domain.SessionMatch
import com.kevinbevan.rivals.model.Invite
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Player
import com.kevinbevan.rivals.model.Rivalry
import com.kevinbevan.rivals.model.RivalryStatus
import com.kevinbevan.rivals.model.Session
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** What came of inviting someone by email. */
enum class InviteResult {
    /** They'll see it next time they open Rivals. */
    SENT,

    /** They'd already invited you, so it's accepted: you're rivals. */
    ACCEPTED,

    /** You're already rivals (or your invite is still waiting). */
    ALREADY,
}

/**
 * Rivalries, invites and the rivals' sessions, in Firestore.
 *
 * Everything is scoped by membership: every query asks for docs whose `playerIds` include
 * the signed-in player, which is exactly what the rules allow.
 */
class RivalryRepository(
    private val db: FirebaseFirestore,
    private val store: FirestoreSessionStore,
    private val rules: ScoreRules,
) {
    private val rivalries = db.collection(Schema.RIVALRIES)
    private val invites = db.collection(Schema.INVITES)
    private val sessions = db.collection(Schema.SESSIONS)

    // Reads

    /** [me]'s rivalries, including invites either way that haven't been accepted yet. */
    fun observeRivalries(me: String): Flow<List<Rivalry>> =
        rivalries.whereArrayContains(Schema.PLAYER_IDS, me).snapshots().map { snap ->
            snap.documents.map { rivalryFrom(it.id, it.fields()) }
        }

    /** One rivalry, or `null` once the server confirms it's gone (declined or removed). */
    fun observeRivalry(id: String): Flow<Rivalry?> =
        rivalries.document(id).snapshots(MetadataChanges.INCLUDE)
            .filter { it.exists() || !it.metadata.isFromCache }
            .map { snap -> snap.takeIf { it.exists() }?.let { rivalryFrom(it.id, it.fields()) } }
            .distinctUntilChanged()

    /** Every session [me] has played in, across all their rivalries. */
    fun observeSessions(me: String): Flow<Synced<List<Session>>> =
        sessions.whereArrayContains(Schema.PLAYER_IDS, me).snapshots(MetadataChanges.INCLUDE).map { snap ->
            Synced(snap.documents.map { it.toSession() }, snap.metadata.hasPendingWrites())
        }

    /** Every match [me] has played, for stats. A collection-group query over `matches`. */
    fun observeAllMatches(me: String): Flow<List<SessionMatch>> =
        db.collectionGroup(Schema.MATCHES).whereArrayContains(Schema.PLAYER_IDS, me).snapshots().map { snap ->
            snap.documents.map { SessionMatch(sessionId = it.reference.parent.parent!!.id, match = it.toMatch()) }
        }

    /** Every frame [me] has played, for stats. A collection-group query over `frames`. */
    fun observeAllFrames(me: String): Flow<List<SessionFrame>> =
        db.collectionGroup(Schema.FRAMES).whereArrayContains(Schema.PLAYER_IDS, me).snapshots().map { snap ->
            snap.documents.map {
                val matchRef = it.reference.parent.parent!!
                SessionFrame(sessionId = matchRef.parent.parent!!.id, matchId = matchRef.id, frame = it.toFrame())
            }
        }

    // Rivalries

    /**
     * Invites [rival], found by email, to a rivalry with [me]. It stays pending until they
     * accept; if they'd already invited [me], this accepts theirs instead. Needs the network.
     */
    suspend fun invite(me: String, rival: String): InviteResult {
        require(me != rival) { "You can't be your own rival" }
        val ref = rivalries.document(Rivalry.idFor(me, rival))
        val existing = ref.get().await().takeIf { it.exists() }?.let { rivalryFrom(it.id, it.fields()) }
        return when {
            existing == null -> {
                ref.set(
                    mapOf(
                        Schema.PLAYER_IDS to listOf(me, rival).sorted(),
                        Schema.STATUS to RivalryStatus.PENDING.wire,
                        Schema.INVITED_BY to me,
                        Schema.CREATED_AT to FieldValue.serverTimestamp(),
                    ),
                ).await()
                InviteResult.SENT
            }
            existing.status == RivalryStatus.PENDING && existing.invitedBy != me -> {
                accept(existing.id)
                InviteResult.ACCEPTED
            }
            else -> InviteResult.ALREADY
        }
    }

    /** Accepts a pending invite sent to you. */
    suspend fun accept(rivalryId: String) {
        rivalries.document(rivalryId).update(
            mapOf(Schema.STATUS to RivalryStatus.ACTIVE.wire, Schema.ACCEPTED_AT to FieldValue.serverTimestamp()),
        ).await()
    }

    /**
     * Declines or cancels a pending invite, or ends a rivalry. Its sessions stay in both
     * players' history, and inviting each other again picks the rivalry back up.
     */
    suspend fun remove(rivalryId: String) {
        rivalries.document(rivalryId).delete().await()
    }

    // Invite links

    /** Creates a one-off invite code for [me] to share. Needs the network. */
    suspend fun createInvite(me: Player): String {
        val code = newInviteCode()
        invites.document(code).set(
            mapOf(
                Schema.FROM to me.uid,
                Schema.FROM_NAME to me.shortName,
                Schema.CREATED_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
        return code
    }

    /** The invite behind [code], or `null` if it's been used or never existed. Works signed out. */
    suspend fun loadInvite(code: String): Invite? {
        val key = normaliseInviteCode(code).takeIf { it.isNotEmpty() } ?: return null
        val snap = invites.document(key).get().await()
        if (!snap.exists()) return null
        return Invite(key, from = snap.getString(Schema.FROM).orEmpty(), fromName = snap.getString(Schema.FROM_NAME).orEmpty())
    }

    /**
     * Accepts [invite] as [me]: the rivalry becomes active straight away, and the invite is
     * used up. Returns the rivalry's id. Needs the network.
     */
    suspend fun acceptInvite(me: String, invite: Invite): String {
        require(invite.from != me) { "That's your own invite. Send it to your rival." }
        val id = Rivalry.idFor(me, invite.from)
        val ref = rivalries.document(id)
        val existing = ref.get().await().takeIf { it.exists() }?.let { rivalryFrom(it.id, it.fields()) }
        if (existing?.status == RivalryStatus.ACTIVE) return id
        db.batch()
            .set(
                ref,
                mapOf(
                    Schema.PLAYER_IDS to listOf(me, invite.from).sorted(),
                    Schema.STATUS to RivalryStatus.ACTIVE.wire,
                    Schema.INVITED_BY to invite.from,
                    Schema.INVITE_CODE to invite.code,
                    Schema.CREATED_AT to FieldValue.serverTimestamp(),
                    Schema.ACCEPTED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .delete(invites.document(invite.code))
            .commit().await()
        return id
    }

    // Sessions

    /**
     * Starts a session in [rivalry] and returns its id. If one is already active (say the
     * other phone just started one), returns that one instead.
     *
     * The check isn't a transaction, because transactions need the network and a session has
     * to be startable in the pool hall with no signal. Two phones starting a session offline
     * at the same moment could both succeed; readers then pick the oldest.
     */
    suspend fun startSession(rivalry: Rivalry, me: String, settings: MatchSettings, venue: String?): String {
        check(rivalry.status == RivalryStatus.ACTIVE) { "Your rival hasn't accepted yet" }
        val mine = sessions.whereArrayContains(Schema.PLAYER_IDS, me).get().await().documents.map { it.toSession() }
        SessionRepository.oldestActive(mine.filter { it.rivalryId == rivalry.id })?.let { return it.id }
        val outcome = rules.startSession(
            playerIds = listOf(me, rivalry.rivalOf(me)),
            createdBy = me,
            settings = settings,
            venue = venue,
            rivalryId = rivalry.id,
        )
        store.commit(outcome.plan)
        return outcome.sessionId
    }

    /**
     * Copies a guest game ([docs], from [GuestRepository.docsOf]) into [rivalry], with each
     * guest id swapped for the uid in [uidFor]. Queued like any other write, so it works offline.
     */
    fun claimGuestGame(docs: Docs, sessionId: String, uidFor: Map<String, String>, rivalry: Rivalry) {
        check(rivalry.status == RivalryStatus.ACTIVE) { "Your rival hasn't accepted yet" }
        require(uidFor.values.toSet() == rivalry.playerIds.toSet()) { "Match the players to this rivalry" }
        // The session goes in the first batch, so the rules can check each later match and frame against it.
        GuestClaim.plan(docs, sessionId, uidFor, rivalry.id).chunked(SessionRepository.MAX_BATCH).forEach(store::commit)
    }

    companion object {
        // No 0/O, 1/I/L: easy to read out or type from a message.
        private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        private const val CODE_LENGTH = 8
        private val random = SecureRandom()

        fun newInviteCode(): String = String(CharArray(CODE_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

        /** Codes are typed back in by hand sometimes: ignore case, spaces and dashes. */
        fun normaliseInviteCode(code: String): String =
            code.uppercase().filter { it.isLetterOrDigit() }.takeIf { c -> c.all { it in ALPHABET } }.orEmpty()
    }
}
