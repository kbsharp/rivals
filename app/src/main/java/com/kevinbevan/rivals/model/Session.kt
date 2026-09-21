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
)
