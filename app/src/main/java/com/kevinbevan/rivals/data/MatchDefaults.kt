package com.kevinbevan.rivals.data

import android.content.Context
import android.content.SharedPreferences
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings

/**
 * How the last match was set up, kept on the phone so the next one opens the same way.
 *
 * A match doesn't have to name a game, so the first one ever offers none; after that the
 * picker opens on whatever was played last, whichever rivalry it was played in.
 */
class MatchDefaults(private val prefs: SharedPreferences?) {

    constructor(context: Context) : this(
        context.getSharedPreferences("match-defaults", Context.MODE_PRIVATE),
    )

    /** In-memory, for previews and tests. */
    constructor() : this(null)

    private var memory = MatchSettings(gameType = null, raceTo = DEFAULT_RACE)

    var last: MatchSettings
        get() = prefs?.let {
            MatchSettings(
                gameType = GameType.fromWire(it.getString(KEY_GAME, null)),
                raceTo = it.getInt(KEY_RACE, DEFAULT_RACE).takeIf { race -> race >= 1 },
            )
        } ?: memory
        set(value) {
            memory = value
            prefs?.edit()
                ?.putString(KEY_GAME, value.gameType?.wire)
                ?.putInt(KEY_RACE, value.raceTo ?: OPEN_ENDED)
                ?.apply()
        }

    private companion object {
        const val KEY_GAME = "gameType"
        const val KEY_RACE = "raceTo"
        const val DEFAULT_RACE = 5

        /** Stored race for an open-ended match; anything below 1 reads back as `null`. */
        const val OPEN_ENDED = 0
    }
}
