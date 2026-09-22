package com.kevinbevan.rivals.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.SessionRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One player's side of the scoreboard. */
data class PlayerSide(val uid: String, val name: String, val frames: Int, val matches: Int)

/**
 * A match that has just been won, for the panel that dims the board. Both phones work it out
 * from the same snapshot, so the result shows on the rival's phone too.
 */
data class MatchResult(
    val matchId: String,
    val number: Int,
    val gameLabel: String,
    val winnerId: String,
    val winnerName: String,
    val winnerFrames: Int,
    val loserFrames: Int,
    /** What happens next, and where tonight stands: one sentence. */
    val next: String,
)

data class SessionUiState(
    val loading: Boolean = true,
    /** The session has ended (here or on the other phone), or doesn't exist. */
    val ended: Boolean = false,
    val me: PlayerSide? = null,
    val rival: PlayerSide? = null,
    /** The running match; `null` between matches. */
    val match: Match? = null,
    /** Settings for the next match: the latest match's. */
    val lastSettings: MatchSettings = DefaultMatchSettings,
    /** Events tagged on the session's last frame; `null` when no frame has been played. */
    val lastFrameEvents: Set<FrameEvent>? = null,
    /** The match just won, while the panel is still up; `null` once it's been seen. */
    val justWon: MatchResult? = null,
    val canUndo: Boolean = false,
    /** Some of what's on screen is saved on this phone but not yet on the server. */
    val pendingSync: Boolean = false,
    /** One-off message for a snackbar; cleared by [SessionViewModel.dismissMessage]. */
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val sessionId: String,
    /** A quick game on the phone: nobody's signed in to it, so frames are recorded by its creator. */
    private val guest: Boolean,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private data class Local(
        val message: String? = null,
        /** This phone just ended the session, so leave even if the snapshot hasn't caught up. */
        val exited: Boolean = false,
        /** The match whose result has already been seen on this phone. */
        val resultSeen: String? = null,
    )

    private val local = MutableStateFlow(Local())

    private val matches = sessionRepository.observeMatches(sessionId)

    private val session = sessionRepository.observeSession(sessionId)

    private val names = session
        .map { it.value }
        .distinctUntilChanged { a, b -> a?.playerIds == b?.playerIds && a?.names == b?.names }
        .flatMapLatest { s -> if (s == null) flowOf(emptyMap()) else sessionRepository.observeNames(s) }

    private var createdBy: String? = null

    // Frames of the latest two matches, latest first: the last frame is the one events are
    // tagged on, which may be the previous match's winner; both are kept in the cache for undo.
    private val recentFrames = matches
        .map { snap -> snap.value.sortedByDescending { it.number }.take(2).map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else combine(ids.map { sessionRepository.observeFrames(sessionId, it) }) { it.toList() }
        }

    val uiState: StateFlow<SessionUiState> = combine(
        session,
        matches,
        recentFrames,
        names,
        local,
    ) { session, matches, recent, names, local ->
        val s = session.value
        if (s == null || local.exited) return@combine SessionUiState(loading = false, ended = true)
        createdBy = s.createdBy
        val myId = authRepository.currentUser?.uid?.takeIf { !guest && it in s.playerIds } ?: s.playerIds.firstOrNull().orEmpty()
        val rivalId = s.playerIds.firstOrNull { it != myId }.orEmpty()

        val latestFirst = matches.value.sortedByDescending { it.number }
        val latest = latestFirst.getOrNull(0)
        val running = latest?.takeIf { it.status == Status.ACTIVE }
        val lastFrame = recent.firstNotNullOfOrNull { it.value.lastOrNull() }

        fun side(uid: String) = PlayerSide(
            uid = uid,
            name = names[uid] ?: "Player",
            frames = running?.frameWins?.winsOf(uid) ?: 0,
            matches = s.matchWins.winsOf(uid),
        )
        val me = side(myId)
        val rivalSide = side(rivalId)
        SessionUiState(
            loading = false,
            ended = s.status == Status.ENDED,
            me = me,
            rival = rivalSide,
            match = running,
            justWon = justWon(latestFirst, running, local.resultSeen)?.let { (ended, winnerId) ->
                MatchResult(
                    matchId = ended.id,
                    number = ended.number,
                    gameLabel = ended.settings.gameType.label,
                    winnerId = winnerId,
                    winnerName = if (winnerId == myId) me.name else rivalSide.name,
                    winnerFrames = ended.frameWins.winsOf(winnerId),
                    loserFrames = ended.frameWins.filterKeys { it != winnerId }.values.sum(),
                    next = "Match ${running!!.number} starts now. " +
                        "Tonight ${me.matches} – ${rivalSide.matches}.",
                )
            },
            lastSettings = latest?.settings ?: DefaultMatchSettings,
            lastFrameEvents = lastFrame?.events,
            canUndo = latestFirst.take(2).any { it.framesPlayed > 0 },
            pendingSync = session.hasPendingWrites || matches.hasPendingWrites || recent.any { it.hasPendingWrites },
            message = local.message,
        )
    }
        .catch { emit(SessionUiState(loading = false, message = messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        viewModelScope.launch {
            sessionRepository.writeErrors.collect {
                say(if (guest) "Couldn't save the game on this phone: ${messageFor(it)}" else "Couldn't save to the server: ${messageFor(it)}")
            }
        }
    }

    fun recordFrame(winnerId: String) = act {
        val recordedBy = (if (guest) createdBy else authRepository.currentUser?.uid)
            ?: return@act say("Sign in again to record frames")
        // A won match is announced by the panel on the board, not by a snackbar.
        local.update { it.copy(resultSeen = null) }
        sessionRepository.recordFrame(sessionId, winnerId, recordedBy)
    }

    fun toggleEvent(event: FrameEvent) = act {
        if (!sessionRepository.toggleLastFrameEvent(sessionId, event)) say("No frame to tag yet")
    }

    fun undo() = act {
        if (!sessionRepository.undoLastFrame(sessionId)) say("Nothing to undo")
    }

    fun endMatch() = act {
        val winner = sessionRepository.endMatch(sessionId)
        say(if (winner != null) "${nameOf(winner)} wins the match" else "Match ended with no winner")
    }

    fun startMatch(settings: MatchSettings) = act { sessionRepository.startMatch(sessionId, settings) }

    fun changeSettings(settings: MatchSettings) = act { sessionRepository.changeSettings(sessionId, settings) }

    fun endSession() = act {
        sessionRepository.endSession(sessionId)
        local.update { it.copy(exited = true) }
    }

    /** The match-won panel has been read (or timed out): let the next match have the board. */
    fun dismissResult() {
        val seen = uiState.value.justWon?.matchId ?: return
        local.update { it.copy(resultSeen = seen) }
    }

    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun say(message: String) = local.update { it.copy(message = message) }

    private fun nameOf(uid: String): String =
        uiState.value.let { listOfNotNull(it.me, it.rival) }.firstOrNull { it.uid == uid }?.name ?: "Player"

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(messageFor(e))
            }
        }
    }

    companion object {
        /**
         * The match that was just won, if the board should still be showing it: the previous
         * match ended with a winner, the next one has started, and nothing has been played on
         * it yet. Returns the match and its winner.
         */
        private fun justWon(
            latestFirst: List<Match>,
            running: Match?,
            seen: String?,
        ): Pair<Match, String>? {
            if (running == null || running.framesPlayed > 0) return null
            val previous = latestFirst.getOrNull(1) ?: return null
            if (previous.status != Status.ENDED || previous.id == seen) return null
            val winner = previous.winnerId ?: return null
            return previous to winner
        }

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val route = createSavedStateHandle().toRoute<SessionRoute>()
                SessionViewModel(
                    sessionId = route.sessionId,
                    guest = route.guest,
                    authRepository = container.authRepository,
                    sessionRepository = container.sessions(route.guest),
                )
            }
        }
    }
}
