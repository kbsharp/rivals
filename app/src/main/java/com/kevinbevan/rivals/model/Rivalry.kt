package com.kevinbevan.rivals.model

/**
 * Two players who've agreed to keep score against each other. Mirrors `rivalries/{id}`,
 * where the id is [idFor] the pair, so a pair can only ever have one rivalry.
 */
data class Rivalry(
    val id: String,
    val playerIds: List<String>,
    val status: RivalryStatus,
    /** Who sent the invite; the other player accepts it. */
    val invitedBy: String,
) {
    fun rivalOf(uid: String): String = playerIds.firstOrNull { it != uid }.orEmpty()

    companion object {
        /** The rivalry id for two players: their uids in order, joined. The rules check it. */
        fun idFor(a: String, b: String): String = if (a < b) "${a}_$b" else "${b}_$a"

        /** The two uids a rivalry id is made of. */
        fun playersOf(id: String): List<String> = id.split('_').filter { it.isNotEmpty() }
    }
}

enum class RivalryStatus(val wire: String) {
    /** Invited by email, waiting for the other player to accept. */
    PENDING("pending"),
    ACTIVE("active"),
    ;

    companion object {
        fun fromWire(value: String?): RivalryStatus = entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

/** A share link's invite: whoever opens it and accepts becomes [from]'s rival. Mirrors `invites/{code}`. */
data class Invite(val code: String, val from: String, val fromName: String)
