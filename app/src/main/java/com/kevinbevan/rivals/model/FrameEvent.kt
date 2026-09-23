package com.kevinbevan.rivals.model

/**
 * Something notable about how a frame was won, tagged after the fact. [wire] is the value
 * stored in the frame's `events` array. Credited to the frame's winner.
 */
enum class FrameEvent(val wire: String, val label: String) {
    BREAK_AND_RUN("break-and-run", "Break & run"),
    GOLDEN_BREAK("golden-break", "Golden break"),

    /** The other player fouled three times in a row and lost the rack. */
    THREE_FOULS("three-fouls", "Won on three fouls"),
    ;

    companion object {
        /** Unknown values (from a newer app version) are dropped rather than guessed at. */
        fun fromWire(value: String?): FrameEvent? = entries.firstOrNull { it.wire == value }
    }
}
