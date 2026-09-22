package com.kevinbevan.rivals.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.RivalryRepository
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.StatsCalculator
import com.kevinbevan.rivals.model.Rivalry
import com.kevinbevan.rivals.model.displayNames
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.StatsRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val loading: Boolean = true,
    val myName: String = "",
    val rivalName: String = "",
    val stats: Stats? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    rivalryId: String,
    authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    rivalryRepository: RivalryRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = authRepository.authState.filterNotNull().flatMapLatest { user ->
        val myId = user.uid
        val rivalId = Rivalry.playersOf(rivalryId).firstOrNull { it != myId }.orEmpty()
        combine(
            playerRepository.observePlayers(listOf(myId, rivalId)),
            rivalryRepository.observeSessions(myId),
            rivalryRepository.observeAllMatches(myId),
            rivalryRepository.observeAllFrames(myId),
        ) { players, sessions, matches, frames ->
            val names = displayNames(players)
            StatsUiState(
                loading = false,
                myName = names[myId].orEmpty(),
                rivalName = names[rivalId] ?: "Rival",
                // Only this rivalry's sessions; the calculator drops matches and frames from any other.
                stats = StatsCalculator.compute(
                    myId,
                    rivalId,
                    sessions.value.filter { it.rivalryId == rivalryId },
                    matches,
                    frames,
                ),
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .catch { emit(StatsUiState(loading = false, error = messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                StatsViewModel(
                    rivalryId = createSavedStateHandle().toRoute<StatsRoute>().rivalryId,
                    authRepository = container.authRepository,
                    playerRepository = container.playerRepository,
                    rivalryRepository = container.rivalryRepository,
                )
            }
        }
    }
}
