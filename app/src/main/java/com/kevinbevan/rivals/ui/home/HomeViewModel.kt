package com.kevinbevan.rivals.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tonight's session, as far as Home needs to know. */
data class ActiveSessionSummary(val id: String, val myWins: Int, val rivalWins: Int)

data class HomeUiState(
    val loading: Boolean = true,
    val myName: String = "",
    /** Both `null` until the other player has signed in once and has a `players` doc. */
    val rivalId: String? = null,
    val rivalName: String? = null,
    val myWins: Int = 0,
    val rivalWins: Int = 0,
    val activeSession: ActiveSessionSummary? = null,
    val pendingSync: Boolean = false,
    val starting: Boolean = false,
    val error: String? = null,
    /** One-off navigation to a session; cleared by [HomeViewModel.onSessionOpened]. */
    val openSessionId: String? = null,
)

class HomeViewModel(
    private val authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private data class LocalState(
        val starting: Boolean = false,
        val error: String? = null,
        val openSessionId: String? = null,
    )

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<HomeUiState> = combine(
        authRepository.authState,
        playerRepository.observePlayers(),
        sessionRepository.observeSessions(),
        local,
    ) { user, players, sessions, local ->
        val myId = user?.uid
        val rival = players.firstOrNull { it.uid != myId }
        val totals = ScoreRules.headToHead(sessions.value)
        val active = SessionRepository.oldestActive(sessions.value)
        HomeUiState(
            loading = false,
            myName = players.firstOrNull { it.uid == myId }?.shortName ?: user?.displayName.orEmpty(),
            rivalId = rival?.uid,
            rivalName = rival?.shortName,
            myWins = myId?.let { totals.winsOf(it) } ?: 0,
            rivalWins = rival?.let { totals.winsOf(it.uid) } ?: 0,
            activeSession = active?.let {
                ActiveSessionSummary(
                    id = it.id,
                    myWins = myId?.let(it.matchWins::winsOf) ?: 0,
                    rivalWins = rival?.uid?.let(it.matchWins::winsOf) ?: 0,
                )
            },
            pendingSync = sessions.hasPendingWrites,
            starting = local.starting,
            error = local.error,
            openSessionId = local.openSessionId,
        )
    }
        .catch { emit(HomeUiState(loading = false, error = authRepository.messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startSession(settings: MatchSettings) {
        val myId = authRepository.currentUser?.uid ?: return
        val rival = uiState.value.rivalId ?: return
        if (local.value.starting) return
        local.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = sessionRepository.startSession(myId, rival, settings)
                local.update { it.copy(starting = false, openSessionId = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = authRepository.messageFor(e)
                local.update { it.copy(starting = false, error = "Couldn't start the session: $message") }
            }
        }
    }

    fun onSessionOpened() = local.update { it.copy(openSessionId = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    /** Navigation back to sign-in follows from the auth state change. */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.authRepository, container.playerRepository, container.sessionRepository)
            }
        }
    }
}
