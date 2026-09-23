package com.kevinbevan.rivals.model

/**
 * Pool variant for a match, when one is named. [wire] is the value stored in Firestore.
 *
 * American games only for now. A match needn't name a game at all — it can just track a
 * score — so a match's game type is nullable, and a wire value we don't know (an older
 * `8-ball`, say) reads back as no game rather than a wrong one.
 */
enum class GameType(val wire: String, val label: String) {
    NINE_BALL("9-ball", "9-ball"),
    TEN_BALL("10-ball", "10-ball"),
    ;

    companion object {
        fun fromWire(value: String?): GameType? = entries.firstOrNull { it.wire == value }
    }
}
