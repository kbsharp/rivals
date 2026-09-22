package com.kevinbevan.rivals.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.StatsCalculator
import com.kevinbevan.rivals.model.displayNames
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val loading: Boolean = true,
    val myName: String = "",
    val rivalName: String = "",
    /** `null` until the rival has signed in once. */
    val stats: Stats? = null,
    val error: String? = null,
)

class StatsViewModel(
    authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        authRepository.authState,
        playerRepository.observePlayers(),
        sessionRepository.observeSessions(),
        sessionRepository.observeAllMatches(),
        sessionRepository.observeAllFrames(),
    ) { user, players, sessions, matches, frames ->
        val myId = user?.uid.orEmpty()
        val rivalId = players.firstOrNull { it.uid != myId }?.uid
        val names = displayNames(players)
        StatsUiState(
            loading = false,
            myName = names[myId].orEmpty(),
            rivalName = rivalId?.let(names::get) ?: "Rival",
            stats = rivalId?.let { StatsCalculator.compute(myId, it, sessions.value, matches, frames) },
        )
    }
        .flowOn(Dispatchers.Default)
        .catch { emit(StatsUiState(loading = false, error = authRepository.messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                StatsViewModel(container.authRepository, container.playerRepository, container.sessionRepository)
            }
        }
    }
}
