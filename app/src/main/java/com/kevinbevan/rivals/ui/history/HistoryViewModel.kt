package com.kevinbevan.rivals.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.RivalryRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.model.Rivalry
import com.kevinbevan.rivals.model.displayNames
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.HistoryRoute
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    rivalryId: String,
    authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    rivalryRepository: RivalryRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = authRepository.authState.filterNotNull().flatMapLatest { user ->
        val myId = user.uid
        val rivalId = Rivalry.playersOf(rivalryId).firstOrNull { it != myId }.orEmpty()
        combine(
            playerRepository.observePlayers(listOf(myId, rivalId)),
            rivalryRepository.observeSessions(myId),
        ) { players, sessions ->
            val names = displayNames(players)
            HistoryUiState(
                loading = false,
                myName = names[myId].orEmpty(),
                rivalName = names[rivalId] ?: "Rival",
                items = SessionRepository.pastSessions(sessions.value.filter { it.rivalryId == rivalryId }).map { s ->
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
    }
        .catch { emit(HistoryUiState(loading = false, error = messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HistoryViewModel(
                    rivalryId = createSavedStateHandle().toRoute<HistoryRoute>().rivalryId,
                    authRepository = container.authRepository,
                    playerRepository = container.playerRepository,
                    rivalryRepository = container.rivalryRepository,
                )
            }
        }
    }
}
