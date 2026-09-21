package com.kevinbevan.rivals.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.data.Synced
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
    /** Frame winners' names for the running match, oldest first. */
    val frameWinners: List<String> = emptyList(),
    val canUndo: Boolean = false,
    /** Some of what's on screen is saved on this phone but not yet on the server. */
    val pendingSync: Boolean = false,
    /** One-off message for a snackbar; cleared by [SessionViewModel.dismissMessage]. */
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    private val matches = sessionRepository.observeMatches(sessionId)

    // Listening to the latest match's frames also keeps them in the cache for undo.
    private val latestFrames = matches
        .map { snap -> snap.value.maxByOrNull { it.number }?.id }
        .distinctUntilChanged()
        .flatMapLatest { matchId ->
            if (matchId == null) flowOf(Synced(emptyList(), false))
            else sessionRepository.observeFrames(sessionId, matchId)
        }

    val uiState: StateFlow<SessionUiState> = combine(
        sessionRepository.observeSession(sessionId),
        matches,
        latestFrames,
        playerRepository.observePlayers(),
        message,
    ) { session, matches, frames, players, message ->
        val s = session.value
            ?: return@combine SessionUiState(loading = false, ended = true, message = message)
        val names = players.associate { it.uid to it.shortName }
        val myId = authRepository.currentUser?.uid?.takeIf { it in s.playerIds } ?: s.playerIds.firstOrNull().orEmpty()
        val rivalId = s.playerIds.firstOrNull { it != myId }.orEmpty()

        val latestFirst = matches.value.sortedByDescending { it.number }
        val latest = latestFirst.getOrNull(0)
        val running = latest?.takeIf { it.status == Status.ACTIVE }
        fun side(uid: String) = PlayerSide(
            uid = uid,
            name = names[uid] ?: "Player",
            frames = running?.frameWins?.winsOf(uid) ?: 0,
            matches = s.matchWins.winsOf(uid),
        )
        SessionUiState(
            loading = false,
            ended = s.status == Status.ENDED,
            me = side(myId),
            rival = side(rivalId),
            match = running,
            lastSettings = latest?.settings ?: DefaultMatchSettings,
            frameWinners = if (running == null) emptyList() else frames.value.map { names[it.winnerId] ?: "?" },
            canUndo = latestFirst.take(2).any { it.framesPlayed > 0 },
            pendingSync = session.hasPendingWrites || matches.hasPendingWrites || frames.hasPendingWrites,
            message = message,
        )
    }
        .catch { emit(SessionUiState(loading = false, message = authRepository.messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        viewModelScope.launch {
            sessionRepository.writeErrors.collect {
                message.value = "Couldn't save to the server: ${authRepository.messageFor(it)}"
            }
        }
    }

    fun recordFrame(winnerId: String) = act {
        val recordedBy = authRepository.currentUser?.uid ?: return@act
        val outcome = sessionRepository.recordFrame(sessionId, winnerId, recordedBy)
        if (outcome.matchEnded) {
            message.value = "${nameOf(winnerId)} wins the match!"
        }
    }

    fun undo() = act {
        if (!sessionRepository.undoLastFrame(sessionId)) message.value = "Nothing to undo"
    }

    fun endMatch() = act {
        val winner = sessionRepository.endMatch(sessionId)
        message.value = if (winner != null) "${nameOf(winner)} wins the match" else "Match ended with no winner"
    }

    fun startMatch(settings: MatchSettings) = act { sessionRepository.startMatch(sessionId, settings) }

    fun changeSettings(settings: MatchSettings) = act { sessionRepository.changeSettings(sessionId, settings) }

    fun endSession() = act { sessionRepository.endSession(sessionId) }

    fun dismissMessage() = message.update { null }

    private fun nameOf(uid: String): String =
        uiState.value.let { listOfNotNull(it.me, it.rival) }.firstOrNull { it.uid == uid }?.name ?: "Player"

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = authRepository.messageFor(e)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SessionViewModel(
                    sessionId = createSavedStateHandle().toRoute<SessionRoute>().sessionId,
                    authRepository = container.authRepository,
                    playerRepository = container.playerRepository,
                    sessionRepository = container.sessionRepository,
                )
            }
        }
    }
}
