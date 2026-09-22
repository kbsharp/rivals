package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.domain.DocPath.FrameDoc
import com.kevinbevan.rivals.domain.DocPath.MatchDoc
import com.kevinbevan.rivals.domain.DocPath.SessionDoc

/**
 * Turning a guest game kept on the phone into a rivalry's session: the same documents, with
 * the stand-in guest ids swapped for the rivals' uids.
 */
object GuestClaim {

    /**
     * The writes that copy guest session [sessionId] out of [docs] into [rivalryId], with
     * each guest id replaced by its uid in [uidFor]. The session comes first, then its matches,
     * then frames, so the plan can be split into batches in order.
     */
    fun plan(docs: Docs, sessionId: String, uidFor: Map<String, String>, rivalryId: String): WritePlan {
        val session = checkNotNull(docs[SessionDoc(sessionId)]) { "No guest game $sessionId" }
        val guestIds = (session[Schema.PLAYER_IDS] as? List<*>)?.filterIsInstance<String>().orEmpty()
        require(guestIds.size == 2 && guestIds.all { it in uidFor }) { "Map both players $guestIds to uids" }
        require(uidFor.values.distinct().size == uidFor.size) { "Two guests can't be the same player" }
        val playerIds = guestIds.map(uidFor::getValue)

        fun id(value: Any?): Any? = (value as? String)?.let { uidFor[it] ?: it }
        fun tally(value: Any?): Map<String, Any?> =
            (value as? Map<*, *>).orEmpty().entries.associate { (k, v) -> uidFor.getValue(k as String) to v }
        fun Map<String, Any?>.swap(vararg idFields: String, tallyField: String? = null) = buildMap {
            putAll(this@swap)
            idFields.forEach { f -> if (f in this@swap) put(f, id(this@swap[f])) }
            if (tallyField != null) put(tallyField, tally(this@swap[tallyField]))
            put(Schema.PLAYER_IDS, playerIds)
        }

        val sessionWrite = Write.Set(
            SessionDoc(sessionId),
            session.swap(Schema.CREATED_BY, tallyField = Schema.MATCH_WINS) - Schema.NAMES +
                (Schema.RIVALRY_ID to rivalryId),
        )
        val matches = docs.filterKeys { it is MatchDoc && it.sessionId == sessionId }
            .map { (path, d) -> Write.Set(path, d.swap(Schema.WINNER_ID, tallyField = Schema.FRAME_WINS)) }
        val frames = docs.filterKeys { it is FrameDoc && it.sessionId == sessionId }
            .map { (path, d) -> Write.Set(path, d.swap(Schema.WINNER_ID, Schema.BREAKER_ID, Schema.RECORDED_BY)) }
        return listOf(sessionWrite) + matches + frames
    }

    /** Every document of guest session [sessionId], for deleting it from the phone. */
    fun paths(docs: Docs, sessionId: String): List<DocPath> = docs.keys.filter {
        when (it) {
            is SessionDoc -> it.sessionId == sessionId
            is MatchDoc -> it.sessionId == sessionId
            is FrameDoc -> it.sessionId == sessionId
        }
    }
}
