package com.kevinbevan.rivals.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One past session, from the signed-in player's side. */
data class HistoryItem(
    val id: String,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val venue: String?,
    val myWins: Int,
    val rivalWins: Int,
)

data class HistoryUiState(
    val loading: Boolean = true,
    val myName: String = "",
    val rivalName: String = "",
    /** Newest first. */
    val items: List<HistoryItem> = emptyList(),
    val error: String? = null,
)

class HistoryViewModel(
    authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        authRepository.authState,
        playerRepository.observePlayers(),
        sessionRepository.observeSessions(),
    ) { user, players, sessions ->
        val myId = user?.uid.orEmpty()
        HistoryUiState(
            loading = false,
            myName = players.firstOrNull { it.uid == myId }?.shortName.orEmpty(),
            rivalName = players.firstOrNull { it.uid != myId }?.shortName ?: "Rival",
            items = SessionRepository.pastSessions(sessions.value).map { s ->
                val rivalId = s.playerIds.firstOrNull { it != myId }.orEmpty()
                HistoryItem(
                    id = s.id,
                    startedAt = s.startedAt,
                    endedAt = s.endedAt,
                    venue = s.venue,
                    myWins = s.matchWins.winsOf(myId),
                    rivalWins = s.matchWins.winsOf(rivalId),
                )
            },
        )
    }
        .catch { emit(HistoryUiState(loading = false, error = authRepository.messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HistoryViewModel(container.authRepository, container.playerRepository, container.sessionRepository)
            }
        }
    }
}
