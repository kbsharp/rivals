package com.kevinbevan.rivals.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.kevinbevan.rivals.domain.DocPath
import com.kevinbevan.rivals.domain.FieldOp
import com.kevinbevan.rivals.domain.Schema
import com.kevinbevan.rivals.domain.Write
import com.kevinbevan.rivals.domain.WritePlan
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import java.time.Instant

// Document data -> models. Pure functions over the raw field map, so tests can use them too.
// Firestore hands back whole numbers as Long and times as Timestamp; anything missing or
// malformed falls back to a safe default rather than crashing the listener.

fun sessionFrom(id: String, d: Map<String, Any?>) = Session(
    id = id,
    playerIds = (d[Schema.PLAYER_IDS] as? List<*>)?.filterIsInstance<String>().orEmpty(),
    status = Status.fromWire(d[Schema.STATUS] as? String),
    startedAt = instant(d[Schema.STARTED_AT]),
    endedAt = instant(d[Schema.ENDED_AT]),
    venue = d[Schema.VENUE] as? String,
    createdBy = d[Schema.CREATED_BY] as? String ?: "",
    matchWins = tally(d[Schema.MATCH_WINS]),
)

fun matchFrom(id: String, d: Map<String, Any?>) = Match(
    id = id,
    number = int(d[Schema.NUMBER]) ?: 0,
    settings = MatchSettings(
        gameType = GameType.fromWire(d[Schema.GAME_TYPE] as? String),
        raceTo = int(d[Schema.RACE_TO])?.takeIf { it >= 1 },
    ),
    status = Status.fromWire(d[Schema.STATUS] as? String),
    frameWins = tally(d[Schema.FRAME_WINS]),
    winnerId = d[Schema.WINNER_ID] as? String,
    startedAt = instant(d[Schema.STARTED_AT]),
    endedAt = instant(d[Schema.ENDED_AT]),
)

fun frameFrom(id: String, d: Map<String, Any?>) = Frame(
    id = id,
    number = int(d[Schema.NUMBER]) ?: 0,
    winnerId = d[Schema.WINNER_ID] as? String ?: "",
    breakerId = d[Schema.BREAKER_ID] as? String,
    recordedBy = d[Schema.RECORDED_BY] as? String ?: "",
    recordedAt = instant(d[Schema.RECORDED_AT]),
)

private fun int(value: Any?): Int? = (value as? Number)?.toInt()

private fun instant(value: Any?): Instant? = (value as? Timestamp)?.toInstant()

private fun tally(raw: Any?): Map<String, Int> =
    (raw as? Map<*, *>).orEmpty().entries
        .mapNotNull { (k, v) -> (k as? String)?.let { key -> int(v)?.let { key to it } } }
        .toMap()

// Write plans -> a Firestore batch.

fun FirebaseFirestore.ref(path: DocPath): DocumentReference = when (path) {
    is DocPath.SessionDoc -> collection(Schema.SESSIONS).document(path.sessionId)
    is DocPath.MatchDoc -> ref(DocPath.SessionDoc(path.sessionId))
        .collection(Schema.MATCHES).document(path.matchId)
    is DocPath.FrameDoc -> ref(DocPath.MatchDoc(path.sessionId, path.matchId))
        .collection(Schema.FRAMES).document(path.frameId)
}

fun FirebaseFirestore.batchOf(plan: WritePlan): WriteBatch = batch().also { batch ->
    for (write in plan) {
        val ref = ref(write.doc)
        when (write) {
            is Write.Set -> batch.set(ref, write.fields.toFirestore())
            is Write.Update -> batch.update(ref, write.fields.toFirestore())
            is Write.Delete -> batch.delete(ref)
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.toFirestore(): Map<String, Any> =
    mapValues { toFirestore(it.value) } as Map<String, Any>

private fun toFirestore(value: Any?): Any? = when (value) {
    is FieldOp.Increment -> FieldValue.increment(value.by)
    FieldOp.ServerTimestamp -> FieldValue.serverTimestamp()
    FieldOp.Delete -> FieldValue.delete()
    is Map<*, *> -> value.mapValues { toFirestore(it.value) }
    else -> value
}
