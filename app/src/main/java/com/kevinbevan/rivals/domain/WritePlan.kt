package com.kevinbevan.rivals.domain

/** A document the plan touches. The repository turns these into Firestore references. */
sealed interface DocPath {
    data class SessionDoc(val sessionId: String) : DocPath
    data class MatchDoc(val sessionId: String, val matchId: String) : DocPath
    data class FrameDoc(val sessionId: String, val matchId: String, val frameId: String) : DocPath
}

/** Sentinel field values that map onto Firestore's `FieldValue`s. */
sealed interface FieldOp {
    data class Increment(val by: Long) : FieldOp
    data object ServerTimestamp : FieldOp
    data object Delete : FieldOp
}

/**
 * One document mutation. Field values are plain values (String, Int, Long, Map, List, null)
 * or a [FieldOp]. [Update] keys may be dotted paths into maps; [Set] keys may not.
 */
sealed interface Write {
    val doc: DocPath

    data class Set(override val doc: DocPath, val fields: Map<String, Any?>) : Write
    data class Update(override val doc: DocPath, val fields: Map<String, Any?>) : Write
    data class Delete(override val doc: DocPath) : Write
}

/** Mutations that must be committed together, as a single Firestore `WriteBatch`. */
typealias WritePlan = List<Write>
