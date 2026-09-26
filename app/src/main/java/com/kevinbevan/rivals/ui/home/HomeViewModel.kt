package com.kevinbevan.rivals.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.GuestRepository
import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.RivalryRepository
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Rivalry
import com.kevinbevan.rivals.model.RivalryStatus
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.capitalised
import com.kevinbevan.rivals.model.displayNames
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.SessionRoute
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A rival you've agreed a rivalry with, and your all-time record against them. */
data class RivalCard(
    val rivalryId: String,
    val name: String,
    val myWins: Int,
    val rivalWins: Int,
    /** The session running right now, if there is one. */
    val activeSessionId: String? = null,
    /** That session's match score, yours first. */
    val tonight: Pair<Int, Int>? = null,
    /**
     * The last ten nights, oldest first: `true` you won it, `false` they did, `null` drawn.
     * Nights rather than matches, because Home reads sessions and matches would be a
     * collection-group query per rivalry.
     */
    val nights: List<Boolean?> = emptyList(),
    /** Venues from past nights, most recent first, to offer when starting one. */
    val recentVenues: List<String> = emptyList(),
) {
    val live: Boolean get() = activeSessionId != null
}

/** How many nights the form bar on Home looks back over. */
private const val FORM_NIGHTS = 10

/** A rivalry that hasn't been accepted yet: sent to you ([incoming]) or by you. */
data class InviteCard(val rivalryId: String, val name: String, val incoming: Boolean)

/** One side of a guest game. */
data class GuestSide(val id: String, val name: String, val wins: Int)

/** A quick game kept on the phone. */
data class GuestGame(
    val id: String,
    val active: Boolean,
    val startedAt: Instant?,
    val left: GuestSide,
    val right: GuestSide,
)

data class HomeUiState(
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val myName: String = "",
    val rivals: List<RivalCard> = emptyList(),
    val invites: List<InviteCard> = emptyList(),
    /** Newest first; at most one is active. */
    val guestGames: List<GuestGame> = emptyList(),
    val pendingSync: Boolean = false,
    val message: String? = null,
    /** One-off navigation to a session; cleared by [HomeViewModel.onSessionOpened]. */
    val openSession: SessionRoute? = null,
) {
    val activeGuestGame: GuestGame? get() = guestGames.firstOrNull { it.active }
    val finishedGuestGames: List<GuestGame> get() = guestGames.filter { !it.active }

    /** A finished quick game can go to a rivalry once you're signed in and have a rival. */
    val canSaveGuestGames: Boolean get() = signedIn && rivals.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository,
    private val rivalryRepository: RivalryRepository,
    private val guestRepository: GuestRepository,
) : ViewModel() {

    private data class Local(val message: String? = null, val openSession: SessionRoute? = null)

    /** What Home shows from Firestore, for a signed-in player. */
    private data class Cloud(
        val myName: String = "",
        val rivalries: List<Rivalry> = emptyList(),
        val rivals: List<RivalCard> = emptyList(),
        val invites: List<InviteCard> = emptyList(),
        val pendingSync: Boolean = false,
        val error: String? = null,
    )

    private val local = MutableStateFlow(Local())

    private val cloud: Flow<Cloud?> = authRepository.authState.flatMapLatest { user ->
        if (user == null) return@flatMapLatest flowOf(null)
        val me = user.uid
        combine(rivalryRepository.observeRivalries(me), rivalryRepository.observeSessions(me)) { r, s -> r to s }
            .flatMapLatest { (rivalries, sessions) ->
                playerRepository.observePlayers(listOf(me) + rivalries.map { it.rivalOf(me) }).map { players ->
                    val names = displayNames(players)
                    fun nameOf(uid: String) = names[uid] ?: "Someone"
                    Cloud(
                        myName = names[me] ?: capitalised(user.displayName.orEmpty()),
                        rivalries = rivalries,
                        rivals = rivalries.filter { it.status == RivalryStatus.ACTIVE }.map { r ->
                            val rivalId = r.rivalOf(me)
                            val mine = sessions.value.filter { it.rivalryId == r.id }
                            val totals = ScoreRules.headToHead(mine)
                            val active = SessionRepository.oldestActive(mine)
                            RivalCard(
                                rivalryId = r.id,
                                name = nameOf(rivalId),
                                myWins = totals.winsOf(me),
                                rivalWins = totals.winsOf(rivalId),
                                activeSessionId = active?.id,
                                tonight = active?.let {
                                    it.matchWins.winsOf(me) to it.matchWins.winsOf(rivalId)
                                },
                                nights = nights(mine, me, rivalId),
                                recentVenues = SessionRepository.recentVenues(mine),
                            )
                            // A live rivalry leads, then the rest alphabetically: Home's hero is
                            // whoever you're playing right now.
                        }.sortedWith(compareByDescending<RivalCard> { it.live }.thenBy { it.name.lowercase() }),
                        invites = rivalries.filter { it.status == RivalryStatus.PENDING }.map {
                            InviteCard(it.id, nameOf(it.rivalOf(me)), incoming = it.invitedBy != me)
                        },
                        pendingSync = sessions.hasPendingWrites,
                    )
                }
            }
            .catch { emit(Cloud(myName = capitalised(user.displayName.orEmpty()), error = messageFor(it))) }
    }

    val uiState: StateFlow<HomeUiState> = combine(cloud, guestRepository.observeGames(), local) { cloud, games, local ->
        HomeUiState(
            loading = false,
            signedIn = cloud != null,
            myName = cloud?.myName.orEmpty(),
            rivals = cloud?.rivals.orEmpty(),
            invites = cloud?.invites.orEmpty(),
            guestGames = games.sortedWith(compareByDescending<Session> { it.startedAt }.thenBy { it.id }).map(::guestGame),
            pendingSync = cloud?.pendingSync == true,
            message = local.message ?: cloud?.error,
            openSession = local.openSession,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // Rivalries the signed-in player can save a guest game to.
    private var activeRivalries: List<Rivalry> = emptyList()

    init {
        viewModelScope.launch {
            cloud.collect { c -> activeRivalries = c?.rivalries.orEmpty().filter { it.status == RivalryStatus.ACTIVE } }
        }
    }

    /** Starts a quick game kept on the phone, or resumes the one already running. */
    fun startQuickGame(names: Pair<String, String>, settings: MatchSettings) {
        val id = guestRepository.startGame(names, settings)
        local.update { it.copy(openSession = SessionRoute(id, guest = true)) }
    }

    /** Starts a night against [rivalryId]'s rival and opens it, or opens the one already running. */
    fun startSession(rivalryId: String, settings: MatchSettings, venue: String?) = act("Couldn't start the session") {
        val me = authRepository.currentUser?.uid ?: return@act
        val rivalry = activeRivalries.firstOrNull { it.id == rivalryId } ?: error("That rivalry isn't active")
        val id = rivalryRepository.startSession(rivalry, me, settings, venue)
        local.update { it.copy(openSession = SessionRoute(id, guest = false)) }
    }

    fun acceptInvite(rivalryId: String) = act("Couldn't accept") {
        rivalryRepository.accept(rivalryId)
        say("You're rivals now")
    }

    /** Declines an invite sent to you, or cancels one you sent. */
    fun removeInvite(rivalryId: String) = act("Couldn't remove the invite") { rivalryRepository.remove(rivalryId) }

    /**
     * Saves finished guest game [gameId] to [rivalryId]: [myGuestId] is the side that was the
     * signed-in player, and the other side becomes the rival.
     */
    fun saveGuestGame(gameId: String, rivalryId: String, myGuestId: String) = act("Couldn't save the game") {
        val me = authRepository.currentUser?.uid ?: return@act
        val rivalry = activeRivalries.firstOrNull { it.id == rivalryId } ?: error("That rivalry isn't active")
        val game = uiState.value.guestGames.first { it.id == gameId }
        val otherGuestId = listOf(game.left.id, game.right.id).first { it != myGuestId }
        rivalryRepository.claimGuestGame(
            docs = guestRepository.docsOf(gameId),
            sessionId = gameId,
            uidFor = mapOf(myGuestId to me, otherGuestId to rivalry.rivalOf(me)),
            rivalry = rivalry,
        )
        guestRepository.deleteGame(gameId)
        say("Saved to your rivalry")
    }

    fun onSessionOpened() = local.update { it.copy(openSession = null) }

    fun dismissMessage() = local.update { it.copy(message = null) }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun say(message: String) = local.update { it.copy(message = message) }

    private fun act(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("$failure: ${messageFor(e)}")
            }
        }
    }

    companion object {
        /** Who won each of the last [FORM_NIGHTS] finished nights, oldest first. */
        private fun nights(sessions: List<Session>, me: String, rival: String): List<Boolean?> =
            sessions.filter { it.status == Status.ENDED }
                .sortedWith(compareBy<Session, Instant?>(nullsFirst()) { it.startedAt }.thenBy { it.id })
                .takeLast(FORM_NIGHTS)
                .map { s ->
                    val mine = s.matchWins.winsOf(me)
                    val theirs = s.matchWins.winsOf(rival)
                    if (mine == theirs) null else mine > theirs
                }

        private fun guestGame(s: Session): GuestGame {
            fun side(id: String) = GuestSide(id, s.names[id]?.let(::capitalised) ?: "Player", s.matchWins.winsOf(id))
            return GuestGame(
                id = s.id,
                active = s.status == Status.ACTIVE,
                startedAt = s.startedAt,
                left = side(s.playerIds.getOrElse(0) { GuestRepository.PLAYER_A }),
                right = side(s.playerIds.getOrElse(1) { GuestRepository.PLAYER_B }),
            )
        }

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(
                    container.authRepository,
                    container.playerRepository,
                    container.rivalryRepository,
                    container.guestRepository,
                )
            }
        }
    }
}
