package com.kevinbevan.rivals.domain

import com.kevinbevan.rivals.data.frameFrom
import com.kevinbevan.rivals.data.matchFrom
import com.kevinbevan.rivals.data.sessionFrom
import com.kevinbevan.rivals.domain.DocPath.FrameDoc
import com.kevinbevan.rivals.domain.DocPath.MatchDoc
import com.kevinbevan.rivals.domain.DocPath.SessionDoc
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status

/**
 * In-memory stand-in for Firestore that applies [WritePlan]s the way a `WriteBatch` would,
 * so tests can play out a whole night and check the resulting documents.
 */
class FakeStore {
    val docs = linkedMapOf<DocPath, MutableMap<String, Any?>>()

    fun apply(plan: WritePlan) {
        // Validate the whole batch first so a bad write leaves nothing half-applied.
        plan.filterIsInstance<Write.Update>().forEach {
            check(it.doc in docs || plan.any { w -> w is Write.Set && w.doc == it.doc }) {
                "Update of missing doc ${it.doc}"
            }
        }
        for (write in plan) {
            when (write) {
                is Write.Set -> {
                    check(write.fields.keys.none { '.' in it }) { "Dotted key in Set: ${write.fields.keys}" }
                    docs[write.doc] = write.fields.mapValues { resolve(null, it.value) }.toMutableMap()
                }
                is Write.Update -> {
                    val doc = docs.getValue(write.doc)
                    write.fields.forEach { (path, value) -> updatePath(doc, path.split('.'), value) }
                }
                is Write.Delete -> {
                    check(write.doc in docs) { "Delete of missing doc ${write.doc}" }
                    docs.remove(write.doc)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updatePath(doc: MutableMap<String, Any?>, path: List<String>, value: Any?) {
        if (path.size == 1) {
            if (value == FieldOp.Delete) doc.remove(path[0]) else doc[path[0]] = resolve(doc[path[0]], value)
            return
        }
        val child = (doc[path[0]] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        updatePath(child, path.drop(1), value)
        doc[path[0]] = child
    }

    private fun resolve(current: Any?, value: Any?): Any? = when (value) {
        is FieldOp.Increment -> ((current as? Number)?.toLong() ?: 0L) + value.by
        FieldOp.ServerTimestamp -> TIMESTAMP
        FieldOp.Delete -> error("FieldOp.Delete is only valid in an update")
        is Int -> value.toLong()
        is Map<*, *> -> value.mapValues { resolve(null, it.value) }
        else -> value
    }

    // Readers that map docs back to models with the same mappers the repositories use.

    fun session(id: String): Session = sessionFrom(id, docs.getValue(SessionDoc(id)))

    fun matches(sessionId: String): List<Match> =
        docs.filterKeys { it is MatchDoc && it.sessionId == sessionId }
            .map { (path, d) -> matchFrom((path as MatchDoc).matchId, d) }
            .sortedBy { it.number }

    fun frames(sessionId: String, matchId: String): List<Frame> =
        docs.filterKeys { it is FrameDoc && it.sessionId == sessionId && it.matchId == matchId }
            .map { (path, d) -> frameFrom((path as FrameDoc).frameId, d) }
            .sortedBy { it.number }

    fun activeMatch(sessionId: String): Match? = matches(sessionId).singleOrNull { it.status == Status.ACTIVE }

    fun latestTwo(sessionId: String): Pair<MatchWithLastFrame, MatchWithLastFrame?> {
        val ms = matches(sessionId).takeLast(2).reversed()
            .map { MatchWithLastFrame(it, frames(sessionId, it.id).lastOrNull()) }
        return ms[0] to ms.getOrNull(1)
    }

    companion object {
        const val TIMESTAMP = "<server timestamp>"
    }
}
