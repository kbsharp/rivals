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
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.SessionDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
)

class SessionDetailViewModel(
    sessionId: String,
    authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<SessionDetailUiState> = combine(
        sessionRepository.observeSession(sessionId),
        sessionRepository.observeMatchesWithFrames(sessionId),
        playerRepository.observePlayers(),
    ) { session, matches, players ->
        val s = session.value
            ?: return@combine SessionDetailUiState(loading = false, error = "This session no longer exists.")
        val names = players.associate { it.uid to it.shortName }
        val myId = authRepository.currentUser?.uid?.takeIf { it in s.playerIds } ?: s.playerIds.firstOrNull().orEmpty()
        val rivalId = s.playerIds.firstOrNull { it != myId }.orEmpty()
        fun player(uid: String) = DetailPlayer(uid, names[uid] ?: "Player", s.matchWins.winsOf(uid))
        SessionDetailUiState(
            loading = false,
            session = s,
            me = player(myId),
            rival = player(rivalId),
            matches = matches.value,
        )
    }
        .catch { emit(SessionDetailUiState(loading = false, error = authRepository.messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

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
