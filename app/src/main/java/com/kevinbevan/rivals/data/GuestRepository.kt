package com.kevinbevan.rivals.data

import com.kevinbevan.rivals.domain.Docs
import com.kevinbevan.rivals.domain.GuestClaim
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.domain.Write
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * Quick games with typed-in names, played without an account and kept on the phone until
 * they're saved to a rivalry (see [RivalryRepository.claimGuestGame]) or deleted.
 */
class GuestRepository(
    private val store: LocalSessionStore,
    private val rules: ScoreRules,
) {
    fun observeGames(): Flow<List<Session>> = store.observeSessions()

    /**
     * Starts a game between [names] (left player first) and returns its id. If a guest game
     * is already running, returns that one instead.
     */
    fun startGame(names: Pair<String, String>, settings: MatchSettings): String {
        SessionRepository.oldestActive(store.sessions())?.let { return it.id }
        val ids = listOf(PLAYER_A, PLAYER_B)
        val outcome = rules.startSession(
            playerIds = ids,
            createdBy = PLAYER_A,
            settings = settings,
            names = mapOf(PLAYER_A to names.first.trim().ifEmpty { "Player 1" }, PLAYER_B to names.second.trim().ifEmpty { "Player 2" }),
        )
        store.commit(outcome.plan)
        return outcome.sessionId
    }

    /** The game's documents, for copying it into a rivalry. */
    fun docsOf(sessionId: String): Docs {
        val docs = store.docs.value
        return GuestClaim.paths(docs, sessionId).associateWith(docs::getValue)
    }

    /** Removes the game and everything in it from the phone. */
    fun deleteGame(sessionId: String) {
        val paths = GuestClaim.paths(store.docs.value, sessionId)
        if (paths.isNotEmpty()) store.commit(paths.map { Write.Delete(it) })
    }

    companion object {
        /** Stand-in player ids in a guest game, replaced by uids when it's saved to a rivalry. */
        const val PLAYER_A = "guest-a"
        const val PLAYER_B = "guest-b"
    }
}
