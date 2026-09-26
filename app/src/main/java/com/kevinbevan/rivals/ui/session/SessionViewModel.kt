package com.kevinbevan.rivals.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.kevinbevan.rivals.data.SeenResults
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.Highlight
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.domain.highlightsOf
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.data.Synced
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.model.winsOf
import com.kevinbevan.rivals.ui.appContainer
import com.kevinbevan.rivals.ui.messageFor
import com.kevinbevan.rivals.ui.navigation.SessionRoute
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One player's side of the scoreboard. */
data class PlayerSide(val uid: String, val name: String, val frames: Int, val matches: Int)

/** A finished match, for the list between matches. [winnerId] is `null` when it ended level. */
data class PlayedMatch(
    val number: Int,
    val settings: MatchSettings,
    val winnerId: String?,
    val winnerName: String?,
    /** The winner's frames first; when level, either. */
    val score: Pair<Int, Int>,
    /** Ended by hand before anyone reached the race. */
    val endedEarly: Boolean,
)

/**
 * A match that has just been won, for the panel that dims the board. Both phones work it out
 * from the same snapshot, so the result shows on the rival's phone too.
 */
data class MatchResult(
    val matchId: String,
    val number: Int,
    /** The game played, when the match named one. */
    val gameLabel: String?,
    val winnerId: String,
    val winnerName: String,
    val winnerFrames: Int,
    val loserFrames: Int,
    /** What happens next, and where tonight stands: one sentence. */
    val next: String,
)

/**
 * The session's last frame, for the receipt on the status line: which frame, whose, and from
 * which phone. It's what Undo takes back and what a tag goes on.
 */
data class FrameReceipt(
    val number: Int,
    val winnerId: String,
    val winnerName: String,
    /** Recorded on the other phone. Never set in a guest game, which only has one. */
    val theirPhone: Boolean,
    val events: Set<FrameEvent>,
    /** Set when this frame and the one before it look like one frame recorded on both phones. */
    val double: DoubleFrame? = null,
)

/** Two frames recorded on different phones within [DoubleFrameSeconds] of each other. */
data class DoubleFrame(
    /** The earlier frame's number, when both are in the same match. */
    val first: Int?,
    val secondsApart: Long,
)

/**
 * A line that takes the receipt's place on the status line for a few seconds: what an undo
 * took back, or something that went wrong. [key] tells one notice from the next.
 */
data class Notice(val key: Long, val text: String, val warning: Boolean = false)

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
    /** The number the next match will get. */
    val nextMatchNumber: Int = 1,
    /** Tonight's finished matches that had frames, latest first. */
    val playedMatches: List<PlayedMatch> = emptyList(),
    /** The session's last frame; `null` when no frame has been played. */
    val lastFrame: FrameReceipt? = null,
    /** The match just won, while the panel is still up; `null` once it's been seen. */
    val justWon: MatchResult? = null,
    val canUndo: Boolean = false,
    /** Some of what's on screen is saved on this phone but not yet on the server. */
    val pendingSync: Boolean = false,
    /** Shown on the status line in place of the receipt; cleared by [SessionViewModel.dismissNotice]. */
    val notice: Notice? = null,
    /** The night has ended, here or on the other phone: the full-time panel. */
    val fullTime: FullTime? = null,
)

/** The night, told at the table once it's over, on both phones. */
data class FullTime(
    val venue: String?,
    val length: Duration?,
    /** Who took the night; `null` when it's level. */
    val winnerId: String?,
    /** "Kevin takes the night 3 – 1". */
    val headline: String,
    /** "Four matches, 26 frames. 2 break & runs and a golden break." */
    val summary: String,
    /** All-time match wins, yours first, tonight included; `null` for a quick game. */
    val allTime: Pair<Int, Int>?,
    /** The last match played, to leave on the dimmed board behind the panel. */
    val lastMatch: Match?,
)

/** Two frames from different phones this close together are probably one frame, recorded twice. */
const val DoubleFrameSeconds = 10L

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val sessionId: String,
    /** A quick game on the phone: nobody's signed in to it, so frames are recorded by its creator. */
    private val guest: Boolean,
    /** The signed-in player's uid, if any. */
    private val currentUid: () -> String?,
    private val sessionRepository: SessionRepository,
    /** Which match-won panel this session last showed on this phone. */
    private val seenResults: SeenResults = SeenResults(),
    /** Every session [me] is in, for the all-time score at full time. */
    private val observeSessionsOf: (me: String) -> Flow<List<Session>> = { flowOf(emptyList()) },
) : ViewModel() {

    private data class Local(
        val notice: Notice? = null,
        /** Notices up to this key have had their time on the status line. */
        val noticesSeen: Long = 0,
        /** This phone just ended the session, so leave even if the snapshot hasn't caught up. */
        val exited: Boolean = false,
        /** The match whose result has already been seen on this phone. */
        val resultSeen: String? = null,
    )

    // A panel shown before the board was left isn't shown again on the way back.
    private val local = MutableStateFlow(Local(resultSeen = seenResults[sessionId]))

    private val matches = sessionRepository.observeMatches(sessionId)

    private val session = sessionRepository.observeSession(sessionId)

    private val names = session
        .map { it.value }
        .distinctUntilChanged { a, b -> a?.playerIds == b?.playerIds && a?.names == b?.names }
        .flatMapLatest { s -> if (s == null) flowOf(emptyMap()) else sessionRepository.observeNames(s) }

    private var createdBy: String? = null

    // Frames of the latest two matches, latest first, by match id: the last frame is the one
    // events are tagged on, which may be the previous match's winner; both are kept in the
    // cache for undo.
    private val recentFrames = matches
        .map { snap -> snap.value.sortedByDescending { it.number }.take(2).map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else combine(ids.map { id -> sessionRepository.observeFrames(sessionId, id).map { id to it } }) { it.toList() }
        }

    /** Undos this phone has asked for that haven't shown up in a snapshot yet. */
    private val localUndos = AtomicInteger()

    /** Orders notices, so the newest one has the status line. */
    private val noticeKeys = AtomicLong()

    /**
     * The latest matches and frames, watched for a frame going missing, which only an undo
     * does, so both phones can say what was taken back. The last frame is remembered from one
     * snapshot to the next, since after the undo it's gone.
     */
    private val board = combine(matches, recentFrames) { m, recent -> Board(m, recent, played(m.value, recent)) }
        .runningFold(null as Board?) { before, now ->
            val gone = before?.played?.frame
            if (gone == null || now.played.total >= before.played.total) return@runningFold now.copy(undone = before?.undone)
            val byMe = localUndos.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0
            now.copy(undone = Undone(noticeKeys.incrementAndGet(), byMe, gone, before.played.match!!))
        }
        .filterNotNull()
        // An undo that reopens a match takes back its result, so winning it again is news.
        .onEach { b ->
            val seen = local.value.resultSeen
            if (seen != null && b.matches.value.any { it.id == seen && it.status == Status.ACTIVE }) {
                seenResults[sessionId] = null
                local.update { it.copy(resultSeen = null) }
            }
        }

    /** Once the night is over: every match with its frames, and the all-time score. */
    private val night = session
        .map { it.value?.takeIf { s -> s.status == Status.ENDED } }
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .flatMapLatest { s ->
            if (s == null) flowOf(null)
            else combine(
                sessionRepository.observeMatchesWithFrames(sessionId).map { it.value },
                allTimeOf(s),
            ) { matches, allTime -> Night(matches, allTime) }
        }

    private fun allTimeOf(session: Session): Flow<Map<String, Int>?> {
        val me = currentUid()
        if (guest || me == null || session.rivalryId == null) return flowOf(null)
        return observeSessionsOf(me)
            .map<List<Session>, Map<String, Int>?> { all -> ScoreRules.headToHead(all.filter { it.rivalryId == session.rivalryId }) }
            // The panel doesn't wait for it, and does without it if it can't be read.
            .onStart { emit(null) }
            .catch { emit(null) }
    }

    val uiState: StateFlow<SessionUiState> = combine(
        session,
        board,
        names,
        local,
        night.onStart { emit(null) },
    ) { session, board, names, local, night ->
        val matches = board.matches
        val s = session.value
        if (s == null || local.exited) return@combine SessionUiState(loading = false, ended = true)
        createdBy = s.createdBy
        val myId = currentUid().takeIf { !guest }?.takeIf { it in s.playerIds } ?: s.playerIds.firstOrNull().orEmpty()
        val rivalId = s.playerIds.firstOrNull { it != myId }.orEmpty()

        val latestFirst = matches.value.sortedByDescending { it.number }
        val latest = latestFirst.getOrNull(0)
        val running = latest?.takeIf { it.status == Status.ACTIVE }
        val played = board.played

        fun side(uid: String) = PlayerSide(
            uid = uid,
            name = names[uid] ?: "Player",
            frames = running?.frameWins?.winsOf(uid) ?: 0,
            matches = s.matchWins.winsOf(uid),
        )
        val me = side(myId)
        val rivalSide = side(rivalId)
        if (s.status == Status.ENDED) {
            val last = latestFirst.firstOrNull { it.framesPlayed > 0 }
            return@combine SessionUiState(
                loading = false,
                me = me.copy(frames = last?.frameWins?.winsOf(myId) ?: 0),
                rival = rivalSide.copy(frames = last?.frameWins?.winsOf(rivalId) ?: 0),
                fullTime = fullTime(s, night, me, rivalSide, last),
            )
        }
        SessionUiState(
            loading = false,
            ended = s.status == Status.ENDED,
            me = me,
            rival = rivalSide,
            match = running,
            justWon = justWon(latestFirst, running, local.resultSeen)?.let { (ended, winnerId) ->
                MatchResult(
                    matchId = ended.id,
                    number = ended.number,
                    gameLabel = ended.settings.gameType?.label,
                    winnerId = winnerId,
                    winnerName = if (winnerId == myId) me.name else rivalSide.name,
                    winnerFrames = ended.frameWins.winsOf(winnerId),
                    loserFrames = ended.frameWins.filterKeys { it != winnerId }.values.sum(),
                    next = "Match ${running!!.number} starts now. " +
                        "Tonight ${me.matches} – ${rivalSide.matches}.",
                )
            },
            lastSettings = latest?.settings ?: DefaultMatchSettings,
            nextMatchNumber = (latest?.number ?: 0) + 1,
            playedMatches = latestFirst
                .filter { it.status == Status.ENDED && it.framesPlayed > 0 }
                .map { m ->
                    val winnerId = m.winnerId
                    val mine = m.frameWins.winsOf(myId)
                    val theirs = m.frameWins.winsOf(rivalId)
                    PlayedMatch(
                        number = m.number,
                        settings = m.settings,
                        winnerId = winnerId,
                        winnerName = winnerId?.let { if (it == myId) me.name else rivalSide.name },
                        score = if (winnerId == rivalId) theirs to mine else mine to theirs,
                        endedEarly = m.settings.raceTo.let { it == null || maxOf(mine, theirs) < it },
                    )
                },
            lastFrame = played.frame?.let { frame ->
                FrameReceipt(
                    number = frame.number,
                    winnerId = frame.winnerId,
                    winnerName = if (frame.winnerId == myId) me.name else rivalSide.name,
                    theirPhone = !guest && frame.recordedBy != myId,
                    events = frame.events,
                    double = played.double,
                )
            },
            canUndo = latestFirst.take(2).any { it.framesPlayed > 0 },
            pendingSync = session.hasPendingWrites || matches.hasPendingWrites || board.recent.any { it.second.hasPendingWrites },
            // The newest of the two, until it's been shown for long enough.
            notice = listOfNotNull(local.notice, board.undone?.let { undoNotice(it, me, rivalSide) })
                .filter { it.key > local.noticesSeen }
                .maxByOrNull { it.key },
        )
    }
        .catch { emit(SessionUiState(loading = false, notice = Notice(0, messageFor(it), warning = true))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionUiState())

    init {
        viewModelScope.launch {
            sessionRepository.writeErrors.collect {
                warn(if (guest) "Couldn't save the game on this phone: ${messageFor(it)}" else "Couldn't save to the server: ${messageFor(it)}")
            }
        }
    }

    fun recordFrame(winnerId: String) = act {
        val recordedBy = (if (guest) createdBy else currentUid())
            ?: return@act warn("Sign in again to record frames")
        // A won match is announced by the panel on the board, not by a snackbar.
        local.update { it.copy(resultSeen = null) }
        sessionRepository.recordFrame(sessionId, winnerId, recordedBy)
    }

    fun toggleEvent(event: FrameEvent) = act {
        if (!sessionRepository.toggleLastFrameEvent(sessionId, event)) say("No frame to tag yet")
    }

    fun undo() = act {
        // Counted first: the snapshot showing the frame gone can arrive before this returns.
        localUndos.incrementAndGet()
        if (!sessionRepository.undoLastFrame(sessionId)) {
            localUndos.updateAndGet { (it - 1).coerceAtLeast(0) }
            say("Nothing to undo")
        }
    }

    fun endMatch() = act {
        val number = uiState.value.match?.number
        val winner = sessionRepository.endMatch(sessionId)
        say("Match $number ended · " + if (winner != null) "${nameOf(winner)} wins it" else "no winner")
    }

    fun startMatch(settings: MatchSettings) = act { sessionRepository.startMatch(sessionId, settings) }

    fun changeSettings(settings: MatchSettings) = act { sessionRepository.changeSettings(sessionId, settings) }

    /** Ends the night: the full-time panel follows on both phones, or, if nothing was played, Home. */
    fun endSession() = act {
        if (sessionRepository.endSession(sessionId)) local.update { it.copy(exited = true) }
    }

    /** The match-won panel for [matchId] is on screen: leaving and coming back won't show it again. */
    fun resultShown(matchId: String) {
        seenResults[sessionId] = matchId
    }

    /** The match-won panel has been read (or timed out): let the next match have the board. */
    fun dismissResult() {
        val seen = uiState.value.justWon?.matchId ?: return
        local.update { it.copy(resultSeen = seen) }
    }

    /** [notice] has had its time on the status line. */
    fun dismissNotice(notice: Notice) =
        local.update { it.copy(noticesSeen = maxOf(it.noticesSeen, notice.key)) }

    private fun say(text: String, warning: Boolean = false) =
        local.update { it.copy(notice = Notice(noticeKeys.incrementAndGet(), text, warning)) }

    private fun warn(text: String) = say(text, warning = true)

    private fun nameOf(uid: String): String =
        uiState.value.let { listOfNotNull(it.me, it.rival) }.firstOrNull { it.uid == uid }?.name ?: "Player"

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                warn(messageFor(e))
            }
        }
    }

    /** What was played, as far as the latest two matches tell: the total, and the last frame. */
    private data class Played(
        val total: Int,
        val match: Match? = null,
        val frame: Frame? = null,
        val double: DoubleFrame? = null,
    )

    private data class Night(val matches: List<MatchWithFrames>, val allTime: Map<String, Int>?)

    private fun fullTime(session: Session, night: Night?, me: PlayerSide, rival: PlayerSide, last: Match?): FullTime {
        val winner = when {
            me.matches > rival.matches -> me
            rival.matches > me.matches -> rival
            else -> null
        }
        val loser = if (winner == me) rival else me
        return FullTime(
            venue = session.venue,
            length = session.startedAt?.let { start -> session.endedAt?.let { Duration.between(start, it) } },
            winnerId = winner?.uid,
            // The score never breaks across lines.
            headline = if (winner != null) "${winner.name} takes the night ${winner.matches}\u00A0–\u00A0${loser.matches}"
                else "Level on the night, ${me.matches}\u00A0–\u00A0${rival.matches}",
            summary = nightSummary(night?.matches.orEmpty()),
            allTime = night?.allTime?.let { it.winsOf(me.uid) to it.winsOf(rival.uid) },
            lastMatch = last,
        )
    }

    private data class Undone(val key: Long, val byMe: Boolean, val frame: Frame, val match: Match)

    private data class Board(
        val matches: Synced<List<Match>>,
        val recent: List<Pair<String, Synced<List<Frame>>>>,
        val played: Played,
        /** The latest undo seen while the board was open. */
        val undone: Undone? = null,
    )

    /** "KEVIN UNDID FRAME 6 · 4 – 2 → 3 – 2", scores from this phone's side. */
    private fun undoNotice(undone: Undone, me: PlayerSide, rival: PlayerSide): Notice {
        val wins = undone.match.frameWins
        val before = wins.winsOf(me.uid) to wins.winsOf(rival.uid)
        val after = if (undone.frame.winnerId == me.uid) before.copy(first = before.first - 1)
            else before.copy(second = before.second - 1)
        val who = when {
            guest -> "Frame ${undone.frame.number} undone"
            undone.byMe -> "${me.name} undid frame ${undone.frame.number}"
            else -> "${rival.name} undid frame ${undone.frame.number}"
        }
        return Notice(
            undone.key,
            "$who · ${before.first} – ${before.second} → ${after.first} – ${after.second}",
        )
    }

    companion object {
        /** "Four matches, 26 frames. 2 break & runs, a golden break and a hill-hill decider." */
        internal fun nightSummary(matches: List<MatchWithFrames>): String {
            val played = matches.filter { it.match.framesPlayed > 0 }
            if (played.isEmpty()) return ""
            val frames = played.sumOf { it.match.framesPlayed }
            val counts = played.size.let { "${countWord(it).replaceFirstChar(Char::uppercase)} ${if (it == 1) "match" else "matches"}" } +
                ", $frames ${if (frames == 1) "frame" else "frames"}."
            val tags = played.flatMap { it.frames }.flatMap { it.events }
            val highlights = played.flatMap(::highlightsOf)
            fun some(n: Int, one: String, many: String) = when (n) {
                0 -> null
                1 -> one
                else -> "$n $many"
            }
            val extras = listOfNotNull(
                some(tags.count { it == FrameEvent.BREAK_AND_RUN }, "a break & run", "break & runs"),
                some(tags.count { it == FrameEvent.GOLDEN_BREAK }, "a golden break", "golden breaks"),
                some(tags.count { it == FrameEvent.THREE_FOULS }, "a frame won on three fouls", "frames won on three fouls"),
                some(highlights.count { it is Highlight.HillHill }, "a hill-hill decider", "hill-hill deciders"),
                some(highlights.count { it is Highlight.Comeback }, "a comeback", "comebacks"),
                some(highlights.count { it is Highlight.Shutout }, "a shutout", "shutouts"),
            )
            if (extras.isEmpty()) return counts
            val list = if (extras.size == 1) extras.single() else extras.dropLast(1).joinToString(", ") + " and " + extras.last()
            return "$counts ${list.replaceFirstChar(Char::uppercase)}."
        }

        private fun countWord(n: Int): String =
            listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten").getOrNull(n) ?: "$n"

        private fun played(matches: List<Match>, recent: List<Pair<String, Synced<List<Frame>>>>): Played {
            val latestFirst = matches.sortedByDescending { it.number }
            val total = latestFirst.take(2).sumOf { it.framesPlayed }
            // The frames that belong to the matches as they are now, latest match first.
            val frames = latestFirst.take(2).map { m ->
                m to recent.firstOrNull { it.first == m.id }?.second?.value.orEmpty().sortedBy { it.number }
            }
            val (match, matchFrames) = frames.firstOrNull { it.second.isNotEmpty() } ?: return Played(total)
            val last = matchFrames.last()
            val earlier = matchFrames.getOrNull(matchFrames.size - 2)
                ?: frames.dropWhile { it.first != match }.drop(1).firstOrNull()?.second?.lastOrNull()
            return Played(total, match, last, doubleFrame(earlier, last, sameMatch = earlier in matchFrames))
        }

        /** [last] looks like [earlier] again, recorded on the other phone. */
        internal fun doubleFrame(earlier: Frame?, last: Frame, sameMatch: Boolean): DoubleFrame? {
            if (earlier == null || earlier.recordedBy == last.recordedBy) return null
            val a = earlier.recordedAt ?: return null
            val b = last.recordedAt ?: return null
            val apart = Duration.between(a, b).abs().seconds
            if (apart > DoubleFrameSeconds) return null
            return DoubleFrame(first = earlier.number.takeIf { sameMatch }, secondsApart = apart)
        }

        /**
         * The match that was just won, if the board should still be showing it: the previous
         * match ended with a winner, the next one has started, and nothing has been played on
         * it yet. Returns the match and its winner.
         */
        private fun justWon(
            latestFirst: List<Match>,
            running: Match?,
            seen: String?,
        ): Pair<Match, String>? {
            if (running == null || running.framesPlayed > 0) return null
            val previous = latestFirst.getOrNull(1) ?: return null
            if (previous.status != Status.ENDED || previous.id == seen) return null
            val winner = previous.winnerId ?: return null
            return previous to winner
        }

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val route = createSavedStateHandle().toRoute<SessionRoute>()
                SessionViewModel(
                    sessionId = route.sessionId,
                    guest = route.guest,
                    currentUid = { container.authRepository.currentUser?.uid },
                    sessionRepository = container.sessions(route.guest),
                    seenResults = container.seenResults,
                    observeSessionsOf = { me -> container.rivalryRepository.observeSessions(me).map { it.value } },
                )
            }
        }
    }
}
