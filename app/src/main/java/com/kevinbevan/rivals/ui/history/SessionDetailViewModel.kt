package com.kevinbevan.rivals.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.displayNames
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.SessionDetailRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A player as the detail screen shows them. */
data class DetailPlayer(val uid: String, val name: String, val matchWins: Int)

data class SessionDetailUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val me: DetailPlayer? = null,
    val rival: DetailPlayer? = null,
    /** In order, each with its frames in order. */
    val matches: List<MatchWithFrames> = emptyList(),
    val error: String? = null,
    /** The session was just deleted from this screen; leave it. */
    val deleted: Boolean = false,
)

class SessionDetailViewModel(
    private val sessionId: String,
    private val authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val deleted = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SessionDetailUiState> = combine(
        sessionRepository.observeSession(sessionId),
        sessionRepository.observeMatchesWithFrames(sessionId),
        playerRepository.observePlayers(),
        deleted,
        error,
    ) { session, matches, players, deleted, error ->
        if (deleted) return@combine SessionDetailUiState(loading = false, deleted = true)
        val s = session.value
            ?: return@combine SessionDetailUiState(loading = false, error = "This session no longer exists.")
        val names = displayNames(players)
        val myId = authRepository.currentUser?.uid?.takeIf { it in s.playerIds } ?: s.playerIds.firstOrNull().orEmpty()
        val rivalId = s.playerIds.firstOrNull { it != myId }.orEmpty()
        fun player(uid: String) = DetailPlayer(uid, names[uid] ?: "Player", s.matchWins.winsOf(uid))
        SessionDetailUiState(
            loading = false,
            session = s,
            me = player(myId),
            rival = player(rivalId),
            matches = matches.value,
            error = error,
        )
    }
        .catch { emit(SessionDetailUiState(loading = false, error = authRepository.messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    /** Deletes this session and everything in it. */
    fun delete() {
        viewModelScope.launch {
            try {
                sessionRepository.deleteSession(sessionId)
                deleted.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Couldn't delete the session: ${authRepository.messageFor(e)}"
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SessionDetailViewModel(
                    sessionId = createSavedStateHandle().toRoute<SessionDetailRoute>().sessionId,
                    authRepository = container.authRepository,
                    playerRepository = container.playerRepository,
                    sessionRepository = container.sessionRepository,
                )
            }
        }
    }
}
