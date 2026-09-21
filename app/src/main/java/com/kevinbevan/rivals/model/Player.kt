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
