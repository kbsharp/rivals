package com.kevinbevan.rivals.data

import com.kevinbevan.rivals.domain.DocPath
import com.kevinbevan.rivals.domain.DocPath.FrameDoc
import com.kevinbevan.rivals.domain.DocPath.MatchDoc
import com.kevinbevan.rivals.domain.DocPath.SessionDoc
import com.kevinbevan.rivals.domain.Docs
import com.kevinbevan.rivals.domain.PlanApplier
import com.kevinbevan.rivals.domain.WritePlan
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.capitalised
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Guest games, kept on the phone: the same documents a rivals' session has in Firestore,
 * held in memory and saved to [file] as JSON after every write. Nothing here needs an
 * account or the network. [file] `null` keeps everything in memory (for tests).
 */
class LocalSessionStore(
    private val file: File?,
    private val now: () -> Instant = Instant::now,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SessionStore {

    private val _docs = MutableStateFlow(file?.let(::read).orEmpty())

    /** Every guest document. */
    val docs: StateFlow<Docs> = _docs.asStateFlow()

    private val saveLock = Mutex()

    private val _writeErrors = MutableSharedFlow<Exception>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val writeErrors: SharedFlow<Exception> = _writeErrors.asSharedFlow()

    /** Every guest game on the phone. */
    fun observeSessions(): Flow<List<Session>> = docs.map(::sessions).distinctUntilChanged()

    fun sessions(docs: Docs = this.docs.value): List<Session> =
        docs.mapNotNull { (path, d) -> (path as? SessionDoc)?.let { sessionFrom(it.sessionId, d) } }

    override fun observeSession(sessionId: String): Flow<Synced<Session?>> =
        docs.map { it[SessionDoc(sessionId)]?.let { d -> sessionFrom(sessionId, d) } }
            .distinctUntilChanged()
            .map { Synced(it, hasPendingWrites = false) }

    override fun observeMatches(sessionId: String): Flow<Synced<List<Match>>> =
        docs.map { matches(it, sessionId) }.distinctUntilChanged().map { Synced(it, hasPendingWrites = false) }

    override fun observeFrames(sessionId: String, matchId: String): Flow<Synced<List<Frame>>> =
        docs.map { frames(it, sessionId, matchId) }.distinctUntilChanged().map { Synced(it, hasPendingWrites = false) }

    override fun observeNames(session: Session): Flow<Map<String, String>> =
        flowOf(session.names.mapValues { capitalised(it.value) })

    override suspend fun loadSession(sessionId: String): Session =
        docs.value[SessionDoc(sessionId)]?.let { sessionFrom(sessionId, it) }
            ?: error("Game $sessionId doesn't exist")

    override suspend fun loadMatches(sessionId: String): List<Match> =
        matches(docs.value, sessionId).sortedByDescending { it.number }

    override suspend fun loadFrames(sessionId: String, match: Match): List<Frame> =
        frames(docs.value, sessionId, match.id)

    override fun commit(plan: WritePlan) {
        _docs.update { PlanApplier.apply(it, plan, now()) }
        save()
    }

    private fun save() {
        val target = file ?: return
        scope.launch {
            // Always writes the latest state, so saves finishing out of order can't lose a write.
            saveLock.withLock {
                try {
                    val tmp = File(target.path + ".tmp")
                    tmp.writeText(encode(docs.value))
                    if (!tmp.renameTo(target)) throw IOException("Couldn't replace $target")
                } catch (e: IOException) {
                    _writeErrors.tryEmit(e)
                }
            }
        }
    }

    private fun matches(docs: Docs, sessionId: String): List<Match> =
        docs.mapNotNull { (path, d) -> (path as? MatchDoc)?.takeIf { it.sessionId == sessionId }?.let { matchFrom(it.matchId, d) } }
            .sortedBy { it.number }

    private fun frames(docs: Docs, sessionId: String, matchId: String): List<Frame> =
        docs.mapNotNull { (path, d) ->
            (path as? FrameDoc)?.takeIf { it.sessionId == sessionId && it.matchId == matchId }?.let { frameFrom(it.frameId, d) }
        }.sortedBy { it.number }

    private fun read(file: File): Docs = try {
        if (file.exists()) decode(file.readText()) else emptyMap()
    } catch (e: Exception) {
        // A corrupt file shouldn't stop the app opening; keep it aside rather than overwrite it.
        file.renameTo(File(file.path + ".corrupt-${System.currentTimeMillis()}"))
        emptyMap()
    }

    companion object {
        private const val INSTANT = "\$instant"

        fun encode(docs: Docs): String = JsonObject(
            docs.entries.associate { (path, fields) -> pathString(path) to toJson(fields) },
        ).toString()

        fun decode(text: String): Docs =
            Json.parseToJsonElement(text).jsonObject.entries.associate { (path, fields) ->
                @Suppress("UNCHECKED_CAST")
                parsePath(path) to (fromJson(fields) as Map<String, Any?>)
            }

        private fun pathString(path: DocPath): String = when (path) {
            is SessionDoc -> "sessions/${path.sessionId}"
            is MatchDoc -> "sessions/${path.sessionId}/matches/${path.matchId}"
            is FrameDoc -> "sessions/${path.sessionId}/matches/${path.matchId}/frames/${path.frameId}"
        }

        private fun parsePath(path: String): DocPath {
            val p = path.split('/')
            return when (p.size) {
                2 -> SessionDoc(p[1])
                4 -> MatchDoc(p[1], p[3])
                6 -> FrameDoc(p[1], p[3], p[5])
                else -> error("Bad path $path")
            }
        }

        private fun toJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value.toLong())
            is Instant -> JsonObject(mapOf(INSTANT to JsonPrimitive(value.toEpochMilli())))
            is List<*> -> JsonArray(value.map(::toJson))
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k as String to toJson(v) })
            else -> error("Can't store ${value::class.simpleName}")
        }

        private fun fromJson(json: JsonElement): Any? = when (json) {
            JsonNull -> null
            is JsonPrimitive -> if (json.isString) json.content else json.booleanOrNull ?: json.longOrNull
            is JsonArray -> json.map(::fromJson)
            is JsonObject -> json[INSTANT]?.takeIf { json.size == 1 }?.let { Instant.ofEpochMilli((it as JsonPrimitive).longOrNull!!) }
                ?: json.mapValues { fromJson(it.value) }
        }
    }
}
