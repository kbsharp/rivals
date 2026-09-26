package com.kevinbevan.rivals.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Which match-won panel each session last showed on this phone, so leaving the board and
 * coming back never shows old news. Kept on the phone: the other phone keeps its own.
 */
class SeenResults(private val prefs: SharedPreferences?) {

    constructor(context: Context) : this(context.getSharedPreferences("seen-results", Context.MODE_PRIVATE))

    /** In-memory, for previews and tests. */
    constructor() : this(null)

    private val memory = mutableMapOf<String, String>()

    /** The match whose result [sessionId] last showed, if any. */
    operator fun get(sessionId: String): String? = prefs?.getString(sessionId, null) ?: memory[sessionId]

    operator fun set(sessionId: String, matchId: String?) {
        if (matchId == null) memory.remove(sessionId) else memory[sessionId] = matchId
        prefs?.edit { if (matchId == null) remove(sessionId) else putString(sessionId, matchId) }
    }
}
