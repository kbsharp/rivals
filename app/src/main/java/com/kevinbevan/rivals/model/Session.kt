package com.kevinbevan.rivals.model

import java.time.Instant

/** One night out. Mirrors `sessions/{sessionId}`. */
data class Session(
    val id: String,
    val playerIds: List<String>,
    val status: Status,
    val startedAt: Instant?,
    val endedAt: Instant? = null,
    val venue: String? = null,
    val createdBy: String,
    /** Denormalised count of matches won, keyed by uid. */
    val matchWins: Map<String, Int>,
    /** The rivalry this night counts towards; `null` for a guest game kept on the phone. */
    val rivalryId: String? = null,
    /** Typed-in names, keyed by player id. Only guest games have them; rivals use their profiles. */
    val names: Map<String, String> = emptyMap(),
)
