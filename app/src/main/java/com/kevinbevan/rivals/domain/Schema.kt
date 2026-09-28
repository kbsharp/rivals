package com.kevinbevan.rivals.domain

/** Firestore collection and field names, shared by the write planner and the repositories. */
object Schema {
    const val SESSIONS = "sessions"
    const val MATCHES = "matches"
    const val FRAMES = "frames"
    const val PLAYERS = "players"
    const val EMAILS = "emails"
    const val RIVALRIES = "rivalries"
    const val INVITES = "invites"

    const val PLAYER_IDS = "playerIds"
    const val STATUS = "status"
    const val STARTED_AT = "startedAt"
    const val ENDED_AT = "endedAt"
    const val VENUE = "venue"
    const val CREATED_BY = "createdBy"
    const val SCORER_ID = "scorerId"
    const val MATCH_WINS = "matchWins"
    const val RIVALRY_ID = "rivalryId"
    const val NAMES = "names"

    const val NUMBER = "number"
    const val GAME_TYPE = "gameType"
    const val RACE_TO = "raceTo"
    const val FRAME_WINS = "frameWins"
    const val WINNER_ID = "winnerId"

    const val BREAKER_ID = "breakerId"
    const val RECORDED_BY = "recordedBy"
    const val RECORDED_AT = "recordedAt"
    const val EVENTS = "events"

    // players/{uid}, emails/{email}
    const val DISPLAY_NAME = "displayName"
    const val EMAIL = "email"
    const val PHOTO_URL = "photoUrl"
    const val CREATED_AT = "createdAt"
    const val UID = "uid"

    // rivalries/{uidA_uidB}, invites/{code}
    const val INVITED_BY = "invitedBy"
    const val INVITE_CODE = "inviteCode"
    const val ACCEPTED_AT = "acceptedAt"
    const val FROM = "from"
    const val FROM_NAME = "fromName"

    /** Dotted path for [Write.Update]. Firebase uids are alphanumeric, so they're safe path segments. */
    fun matchWinsOf(uid: String) = "$MATCH_WINS.$uid"
    fun frameWinsOf(uid: String) = "$FRAME_WINS.$uid"
}
