package com.kevinbevan.rivals.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.kevinbevan.rivals.domain.Schema
import com.kevinbevan.rivals.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Player profiles (`players/{uid}`) and the email index (`emails/{email}` → uid) used to find
 * a rival. The rules let anyone signed in fetch one doc by its id but never list either
 * collection, so the player base can't be browsed: you have to know the exact address.
 */
class PlayerRepository(private val db: FirebaseFirestore) {

    /**
     * Creates or refreshes `players/{uid}` and points the player's email at it.
     * `createdAt` is only written the first time. Runs as a transaction, so it needs the
     * network — fine here, as sign-in does too.
     */
    suspend fun upsert(player: Player) {
        val ref = db.collection(Schema.PLAYERS).document(player.uid)
        val email = normaliseEmail(player.email)
        db.runTransaction { tx ->
            val profile = mapOf(
                Schema.DISPLAY_NAME to player.displayName,
                Schema.EMAIL to player.email,
                Schema.PHOTO_URL to player.photoUrl,
            )
            val exists = tx.get(ref).exists()
            if (exists) {
                tx.set(ref, profile, SetOptions.merge())
            } else {
                tx.set(ref, profile + (Schema.CREATED_AT to FieldValue.serverTimestamp()))
            }
            if (email.isNotEmpty()) tx.set(db.collection(Schema.EMAILS).document(email), mapOf(Schema.UID to player.uid))
        }.await()
    }

    /** The player, or `null` if they have no profile. */
    fun observePlayer(uid: String): Flow<Player?> =
        db.collection(Schema.PLAYERS).document(uid).snapshots().map { snap ->
            snap.takeIf { it.exists() }?.let { playerFrom(it.id, it.fields()) }
        }

    /** Profiles for [uids], in order, skipping any that don't exist. */
    fun observePlayers(uids: List<String>): Flow<List<Player>> =
        if (uids.isEmpty()) flowOf(emptyList())
        else combine(uids.distinct().map(::observePlayer)) { it.filterNotNull() }

    /** Whoever signs in with exactly [email], or `null` if nobody on Rivals does. Needs the network. */
    suspend fun findByEmail(email: String): Player? {
        val key = normaliseEmail(email).takeIf { it.isNotEmpty() && '/' !in it } ?: return null
        val uid = db.collection(Schema.EMAILS).document(key).get().await().getString(Schema.UID) ?: return null
        val snap = db.collection(Schema.PLAYERS).document(uid).get().await()
        return snap.takeIf { it.exists() }?.let { playerFrom(it.id, it.fields()) }
    }

    companion object {
        /** Email index keys: trimmed and lower-cased, matching the rules' `token.email.lower()`. */
        fun normaliseEmail(email: String): String = email.trim().lowercase()
    }
}
