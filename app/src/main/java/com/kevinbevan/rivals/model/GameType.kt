package com.kevinbevan.rivals.model

/** Pool variant for a match. [wire] is the value stored in Firestore. */
enum class GameType(val wire: String, val label: String) {
    EIGHT_BALL("8-ball", "8-ball"),
    NINE_BALL("9-ball", "9-ball"),
    OTHER("other", "Other"),
    ;

    companion object {
        fun fromWire(value: String?): GameType = entries.firstOrNull { it.wire == value } ?: OTHER
    }
}
