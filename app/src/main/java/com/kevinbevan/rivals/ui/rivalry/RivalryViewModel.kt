package com.kevinbevan.rivals.ui.rivalry

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
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.StatsCalculator
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Rivalry
import com.kevinbevan.rivals.model.RivalryStatus
import com.kevinbevan.rivals.model.capitalised
import com.kevinbevan.rivals.model.displayNames
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.RivalryRoute
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tonight's session, as far as the rivalry screen needs to know. */
data class ActiveSessionSummary(val id: String, val myWins: Int, val rivalWins: Int)

/** One past session, from the signed-in player's side. */
data class SessionItem(
    val id: String,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val venue: String?,
    val myWins: Int,
    val rivalWins: Int,
)

/** Which tab the rivalry screen is showing. */
enum class RivalryTab(val label: String) { SESSIONS("Sessions"), STATS("Stats") }

data class RivalryUiState(
    val loading: Boolean = true,
    val myName: String = "",
    val rivalName: String = "Rival",
    /** Sessions can start once the rival has accepted. */
    val accepted: Boolean = true,
    val myWins: Int = 0,
    val rivalWins: Int = 0,
    val activeSession: ActiveSessionSummary? = null,
    /** Venues from past sessions, most recent first, to offer when starting a new one. */
    val recentVenues: List<String> = emptyList(),
    /** Finished sessions, newest first. */
    val sessions: List<SessionItem> = emptyList(),
    /** `null` until the matches and frames have arrived. */
    val stats: Stats? = null,
    val pendingSync: Boolean = false,
    val starting: Boolean = false,
    val error: String? = null,
    /** One-off navigation to a session; cleared by [RivalryViewModel.onSessionOpened]. */
    val openSessionId: String? = null,
    /** The rivalry was removed here or on the other phone; leave. */
    val gone: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RivalryViewModel(
    val rivalryId: String,
    private val authRepository: AuthRepository,
    playerRepository: PlayerRepository,
    private val rivalryRepository: RivalryRepository,
) : ViewModel() {

    private data class LocalState(
        val starting: Boolean = false,
        val error: String? = null,
        val openSessionId: String? = null,
        val removed: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())
    private var rivalry: Rivalry? = null

    val uiState: StateFlow<RivalryUiState> = authRepository.authState.filterNotNull().flatMapLatest { user ->
        val me = user.uid
        // Matches and frames are collection-group queries; they only feed the Stats tab, so they
        // travel together and are combined in one go rather than widening the main combine.
        val statsInput = combine(
            rivalryRepository.observeAllMatches(me),
            rivalryRepository.observeAllFrames(me),
        ) { matches, frames -> matches to frames }
        combine(
            rivalryRepository.observeRivalry(rivalryId),
            rivalryRepository.observeSessions(me),
            playerRepository.observePlayers(Rivalry.playersOf(rivalryId)),
            statsInput,
            local,
        ) { r, allSessions, players, (matches, frames), local ->
            rivalry = r
            if (r == null || local.removed) return@combine RivalryUiState(loading = false, gone = true)
            val rivalId = r.rivalOf(me)
            val names = displayNames(players)
            val sessions = allSessions.value.filter { it.rivalryId == rivalryId }
            val totals = ScoreRules.headToHead(sessions)
            val active = SessionRepository.oldestActive(sessions)
            RivalryUiState(
                loading = false,
                myName = names[me] ?: capitalised(user.displayName.orEmpty()),
                rivalName = names[rivalId] ?: "Rival",
                accepted = r.status == RivalryStatus.ACTIVE,
                myWins = totals.winsOf(me),
                rivalWins = totals.winsOf(rivalId),
                activeSession = active?.let {
                    ActiveSessionSummary(it.id, it.matchWins.winsOf(me), it.matchWins.winsOf(rivalId))
                },
                sessions = SessionRepository.pastSessions(sessions).map { s ->
                    SessionItem(
                        id = s.id,
                        startedAt = s.startedAt,
                        endedAt = s.endedAt,
                        venue = s.venue,
                        myWins = s.matchWins.winsOf(me),
                        rivalWins = s.matchWins.winsOf(rivalId),
                    )
                },
                // The calculator drops any match or frame from a session outside this rivalry.
                stats = StatsCalculator.compute(me, rivalId, sessions, matches, frames),
                recentVenues = SessionRepository.recentVenues(sessions),
                pendingSync = allSessions.hasPendingWrites,
                starting = local.starting,
                error = local.error,
                openSessionId = local.openSessionId,
            )
        }
    }
        // Stats are recomputed from every match and frame whenever any of them change.
        .flowOn(Dispatchers.Default)
        .catch { emit(RivalryUiState(loading = false, error = messageFor(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RivalryUiState())

    fun startSession(settings: MatchSettings, venue: String?) {
        val me = authRepository.currentUser?.uid ?: return
        val r = rivalry ?: return
        if (local.value.starting) return
        local.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = rivalryRepository.startSession(r, me, settings, venue)
                local.update { it.copy(starting = false, openSessionId = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(starting = false, error = "Couldn't start the session: ${messageFor(e)}") }
            }
        }
    }

    /** Ends the rivalry. Its sessions stay in history. */
    fun remove() {
        viewModelScope.launch {
            try {
                rivalryRepository.remove(rivalryId)
                local.update { it.copy(removed = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = "Couldn't remove the rival: ${messageFor(e)}") }
            }
        }
    }

    fun onSessionOpened() = local.update { it.copy(openSessionId = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RivalryViewModel(
                    rivalryId = createSavedStateHandle().toRoute<RivalryRoute>().rivalryId,
                    authRepository = container.authRepository,
                    playerRepository = container.playerRepository,
                    rivalryRepository = container.rivalryRepository,
                )
            }
        }
    }
}
