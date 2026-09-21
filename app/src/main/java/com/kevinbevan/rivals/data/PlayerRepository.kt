package com.kevinbevan.rivals.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.kevinbevan.rivals.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PlayerRepository(private val db: FirebaseFirestore) {

    /**
     * Creates or refreshes `players/{uid}`. `createdAt` is only written the first time.
     * Runs as a transaction, so it needs the network — fine here, as sign-in does too.
     *
     * @throws com.google.firebase.firestore.FirebaseFirestoreException with
     *   [com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED]
     *   when the account isn't on the rules allow-list.
     */
    suspend fun upsert(player: Player) {
        val ref = db.collection(PLAYERS).document(player.uid)
        db.runTransaction { tx ->
            val profile = mapOf(
                "displayName" to player.displayName,
                "email" to player.email,
                "photoUrl" to player.photoUrl,
            )
            if (tx.get(ref).exists()) {
                tx.set(ref, profile, SetOptions.merge())
            } else {
                tx.set(ref, profile + ("createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }

    /** Everyone who has signed in: us two, once the second player has signed in once. */
    fun observePlayers(): Flow<List<Player>> =
        db.collection(PLAYERS).snapshots().map { snap ->
            snap.documents.map {
                Player(
                    uid = it.id,
                    displayName = it.getString("displayName").orEmpty(),
                    email = it.getString("email").orEmpty(),
                    photoUrl = it.getString("photoUrl"),
                )
            }
        }

    private companion object {
        const val PLAYERS = "players"
    }
}
