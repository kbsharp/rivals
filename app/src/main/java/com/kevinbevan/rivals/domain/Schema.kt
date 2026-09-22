package com.kevinbevan.rivals.domain

/** Firestore collection and field names, shared by the write planner and the repositories. */
object Schema {
    const val SESSIONS = "sessions"
    const val MATCHES = "matches"
    const val FRAMES = "frames"

    const val PLAYER_IDS = "playerIds"
    const val STATUS = "status"
    const val STARTED_AT = "startedAt"
    const val ENDED_AT = "endedAt"
    const val VENUE = "venue"
    const val CREATED_BY = "createdBy"
    const val MATCH_WINS = "matchWins"

    const val NUMBER = "number"
    const val GAME_TYPE = "gameType"
    const val RACE_TO = "raceTo"
    const val FRAME_WINS = "frameWins"
    const val WINNER_ID = "winnerId"

    const val BREAKER_ID = "breakerId"
    const val RECORDED_BY = "recordedBy"
    const val RECORDED_AT = "recordedAt"
    const val EVENTS = "events"

    /** Dotted path for [Write.Update]. Firebase uids are alphanumeric, so they're safe path segments. */
    fun matchWinsOf(uid: String) = "$MATCH_WINS.$uid"
    fun frameWinsOf(uid: String) = "$FRAME_WINS.$uid"
}
