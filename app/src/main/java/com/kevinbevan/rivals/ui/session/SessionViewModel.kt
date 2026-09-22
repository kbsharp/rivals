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
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.displayNames
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
    /** Who's breaking the next frame; recorded with it. `null` until someone picks. */
    val breakerId: String? = null,
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

    /** A breaker picked by hand. It only holds until the next frame is recorded (on either phone). */
    private data class BreakerChoice(val afterFrameId: String?, val uid: String)

    private data class Local(
        val message: String? = null,
        val breaker: BreakerChoice? = null,
        /** This phone just ended the session, so leave even if the snapshot hasn't caught up. */
        val exited: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    private val matches = sessionRepository.observeMatches(sessionId)

    // Frames of the latest two matches, latest first: shown on screen, used to alternate the
    // break across a match boundary, and kept in the cache for undo.
    private val recentFrames = matches
        .map { snap -> snap.value.sortedByDescending { it.number }.take(2).map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else combine(ids.map { sessionRepository.observeFrames(sessionId, it) }) { it.toList() }
        }

    /** The frame a manual breaker choice was made after; see [BreakerChoice]. */
    private var lastFrameId: String? = null

    val uiState: StateFlow<SessionUiState> = combine(
        sessionRepository.observeSession(sessionId),
        matches,
        recentFrames,
        playerRepository.observePlayers(),
        local,
    ) { session, matches, recent, players, local ->
        val s = session.value
        if (s == null || local.exited) return@combine SessionUiState(loading = false, ended = true)
        val names = displayNames(players)
        val myId = authRepository.currentUser?.uid?.takeIf { it in s.playerIds } ?: s.playerIds.firstOrNull().orEmpty()
        val rivalId = s.playerIds.firstOrNull { it != myId }.orEmpty()

        val latestFirst = matches.value.sortedByDescending { it.number }
        val latest = latestFirst.getOrNull(0)
        val running = latest?.takeIf { it.status == Status.ACTIVE }
        val latestFrames = recent.getOrNull(0)?.value.orEmpty()
        val newestFirst = recent.flatMap { it.value.asReversed() }
        lastFrameId = newestFirst.firstOrNull()?.id
        val breakerId = local.breaker?.takeIf { it.afterFrameId == lastFrameId }?.uid
            ?: ScoreRules.alternateBreaker(newestFirst.firstNotNullOfOrNull { it.breakerId }, s.playerIds)

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
            frameWinners = if (running == null) emptyList() else latestFrames.map { names[it.winnerId] ?: "?" },
            breakerId = breakerId,
            canUndo = latestFirst.take(2).any { it.framesPlayed > 0 },
            pendingSync = session.hasPendingWrites || matches.hasPendingWrites || recent.any { it.hasPendingWrites },
            message = local.message,
        )
    }
        .catch { emit(SessionUiState(loading = false, message = authRepository.messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        viewModelScope.launch {
            sessionRepository.writeErrors.collect {
                say("Couldn't save to the server: ${authRepository.messageFor(it)}")
            }
        }
    }

    fun recordFrame(winnerId: String) = act {
        val recordedBy = authRepository.currentUser?.uid ?: return@act
        val outcome = sessionRepository.recordFrame(sessionId, winnerId, recordedBy, uiState.value.breakerId)
        if (outcome.matchEnded) say("${nameOf(winnerId)} wins the match!")
    }

    fun chooseBreaker(uid: String) = local.update { it.copy(breaker = BreakerChoice(lastFrameId, uid)) }

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
                say(authRepository.messageFor(e))
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
