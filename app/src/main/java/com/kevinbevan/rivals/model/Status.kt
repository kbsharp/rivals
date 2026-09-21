package com.kevinbevan.rivals.model

/** Lifecycle of a session or a match. [wire] is the value stored in Firestore. */
enum class Status(val wire: String) {
    ACTIVE("active"),
    ENDED("ended"),
    ;

    companion object {
        /** Unknown values read as [ENDED] so a bad doc can never look live. */
        fun fromWire(value: String?): Status = entries.firstOrNull { it.wire == value } ?: ENDED
    }
}
