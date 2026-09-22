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
    var docs: Docs = emptyMap()
        private set

    fun apply(plan: WritePlan) {
        docs = PlanApplier.apply(docs, plan, TIMESTAMP)
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
