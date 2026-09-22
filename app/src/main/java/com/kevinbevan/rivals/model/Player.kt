package com.kevinbevan.rivals.model

/** A signed-in person. Mirrors `players/{uid}`; the doc id is the Firebase Auth uid. */
data class Player(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
) {
    /** First name for tight spaces like the score buttons. */
    val shortName: String
        get() = displayName.substringBefore(' ').ifBlank { email.substringBefore('@') }.ifBlank { "?" }
}

/**
 * Names to show for [players], keyed by uid: first names, unless two players share one
 * (ignoring case), in which case full names, then email names, until they differ.
 */
fun displayNames(players: List<Player>): Map<String, String> {
    val candidates = listOf<(Player) -> String>(
        { it.shortName },
        { it.displayName.ifBlank { it.shortName } },
        { it.email.substringBefore('@').ifBlank { it.uid } },
    )
    for (name in candidates) {
        val names = players.associate { it.uid to name(it) }
        if (names.values.map { it.lowercase() }.distinct().size == names.size) return names
    }
    return players.associate { it.uid to it.uid }
}
