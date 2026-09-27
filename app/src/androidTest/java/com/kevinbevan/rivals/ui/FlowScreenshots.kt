package com.kevinbevan.rivals.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinbevan.rivals.data.MatchDefaults
import com.kevinbevan.rivals.domain.Count
import com.kevinbevan.rivals.domain.GameTypeStats
import com.kevinbevan.rivals.domain.NightsRecord
import com.kevinbevan.rivals.domain.Record
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.Streak
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Invite
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Player
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.history.DetailPlayer
import com.kevinbevan.rivals.ui.history.SessionDetailContent
import com.kevinbevan.rivals.ui.history.SessionDetailUiState
import com.kevinbevan.rivals.ui.home.GuestGame
import com.kevinbevan.rivals.ui.home.GuestSide
import com.kevinbevan.rivals.ui.home.HomeActions
import com.kevinbevan.rivals.ui.home.HomeContent
import com.kevinbevan.rivals.ui.home.HomeUiState
import com.kevinbevan.rivals.ui.home.InviteCard
import com.kevinbevan.rivals.ui.home.RivalCard
import com.kevinbevan.rivals.ui.invite.AddRivalContent
import com.kevinbevan.rivals.ui.invite.AddRivalUiState
import com.kevinbevan.rivals.ui.invite.InviteContent
import com.kevinbevan.rivals.ui.invite.InviteUiState
import com.kevinbevan.rivals.ui.rivalry.ActiveSessionSummary
import com.kevinbevan.rivals.ui.rivalry.RivalryContent
import com.kevinbevan.rivals.ui.rivalry.RivalryUiState
import com.kevinbevan.rivals.ui.rivalry.SessionItem
import com.kevinbevan.rivals.ui.session.DoubleFrame
import com.kevinbevan.rivals.ui.session.FrameReceipt
import com.kevinbevan.rivals.ui.session.FullTime
import com.kevinbevan.rivals.ui.session.MatchResult
import com.kevinbevan.rivals.ui.session.Notice
import com.kevinbevan.rivals.ui.session.PlayerSide
import com.kevinbevan.rivals.ui.session.SessionActions
import com.kevinbevan.rivals.ui.session.SessionContent
import com.kevinbevan.rivals.ui.session.SessionUiState
import com.kevinbevan.rivals.ui.signin.SignInContent
import com.kevinbevan.rivals.ui.signin.SignInUiState
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Not assertions: every state a player can open that [Screenshots] doesn't draw — each menu
 * open, each dialog, loading, pending and error states — in both themes, for the UX review
 * (`design/ux-plan.md`). A menu or dialog is shot on the whole display, over the screen it
 * came from, so it's seen where the player sees it.
 */
@RunWith(Parameterized::class)
class FlowScreenshots(private val theme: String) {
    @get:Rule val compose = createComposeRule()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun themes() = listOf("dark", "light")

        private const val FRAME = "flow-frame"
    }

    private val dark get() = theme == "dark"

    private fun setContent(landscape: Boolean, phone: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            val defaults = remember { MatchDefaults() }
            CompositionLocalProvider(LocalMatchDefaults provides defaults) {
                RivalsTheme(darkTheme = dark) {
                    if (landscape) {
                        // As in Screenshots: the scoreboard at a landscape phone's size, without
                        // the emulator's portrait bars.
                        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(915.dp, 412.dp))) {
                            Box(Modifier.testTag(FRAME).consumeWindowInsets(WindowInsets.safeDrawing)) { content() }
                        }
                    } else if (phone) {
                        // A Pixel-sized portrait window, for a render that depends on the page
                        // outgrowing the screen; the test emulator's own screen is far taller.
                        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(412.dp, 915.dp))) {
                            Box(Modifier.testTag(FRAME)) { content() }
                        }
                    } else {
                        content()
                    }
                }
            }
        }
    }

    /**
     * Renders [content], runs [steps] (opening a menu, say), and shoots what's on the display:
     * the scoreboard's frame in landscape, the whole screen otherwise. A dialog over the
     * landscape board is shot on its own, since the board is drawn scaled into a portrait window.
     */
    private fun shoot(
        name: String,
        landscape: Boolean = false,
        dialog: Boolean = false,
        steps: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        setContent(landscape, content = content)
        compose.waitForIdle()
        steps()
        compose.waitForIdle()
        if (!landscape) {
            // Let the popup's enter animation reach the display before it's read back.
            SystemClock.sleep(600)
            save(name, InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().copy(Bitmap.Config.ARGB_8888, false))
            return
        }
        if (dialog) {
            save(name, compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
            return
        }
        // The board, with the open menu drawn over it. The test window is portrait, so the menu
        // drops below the board; place it as DropdownMenu would in a landscape window: under the
        // menu button if it fits, otherwise above it, kept inside the screen's width.
        val frame = compose.onNodeWithTag(FRAME).fetchSemanticsNode()
        val board = compose.onNodeWithTag(FRAME).captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        val popups = compose.onAllNodes(isPopup())
        if (popups.fetchSemanticsNodes().isNotEmpty()) {
            val menu = popups[0].captureToImage().asAndroidBitmap()
            val anchor = compose.onNodeWithContentDescription("Game menu").fetchSemanticsNode()
            val a = anchor.positionOnScreen - frame.positionOnScreen
            val left = a.x.toInt()
            val right = left + anchor.size.width
            val top = a.y.toInt()
            val bottom = top + anchor.size.height
            val x = (if (left + menu.width <= board.width) left else right - menu.width).coerceAtLeast(0)
            val y = if (bottom + menu.height <= board.height) bottom else (top - menu.height).coerceAtLeast(0)
            Canvas(board).drawBitmap(menu, x.toFloat(), y.toFloat(), null)
        }
        save(name, board)
    }

    /** A notice or snackbar is on a timer; hold the clock so it's still up when the shot is taken. */
    private fun shootMessage(name: String, landscape: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        setContent(landscape, content = content)
        compose.mainClock.advanceTimeBy(1_000)
        val node = if (landscape) compose.onNodeWithTag(FRAME) else compose.onRoot()
        save(name, node.captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        File(dir, "$name-$theme.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String) = compose.onNodeWithText(text).performClick()
    private fun tapIcon(description: String) = compose.onNodeWithContentDescription(description).performClick()

    private val start = Instant.parse("2026-09-21T19:30:00Z")

    // ---- Session (the scoreboard) ----

    private val frame6 = FrameReceipt(6, "a", "Kevin", theirPhone = false, events = emptySet())

    private fun board(
        a: Int = 4,
        b: Int = 2,
        matchesA: Int = 2,
        matchesB: Int = 1,
        race: Int? = 5,
        game: GameType? = GameType.NINE_BALL,
        receipt: FrameReceipt? = frame6,
        canUndo: Boolean = true,
        pending: Boolean = false,
        running: Boolean = true,
        justWon: MatchResult? = null,
        notice: Notice? = null,
        names: Pair<String, String> = "Kevin" to "Julian",
    ) = SessionUiState(
        loading = false,
        me = PlayerSide("a", names.first, frames = a, matches = matchesA),
        rival = PlayerSide("b", names.second, frames = b, matches = matchesB),
        match = Match(
            "m", matchesA + matchesB + 1, MatchSettings(game, race), Status.ACTIVE, mapOf("a" to a, "b" to b),
            startedAt = Instant.now().minusSeconds(754),
        ).takeIf { running },
        lastSettings = MatchSettings(game, race),
        lastFrame = receipt?.copy(winnerName = if (receipt.winnerId == "a") names.first else names.second),
        justWon = justWon,
        canUndo = canUndo,
        pendingSync = pending,
        notice = notice,
    )

    @Composable private fun Board(state: SessionUiState) = SessionContent(state, SessionActions())

    private val won = MatchResult("m4", 4, "9-ball", "a", "Kevin", 5, 2, "Match 5 starts now. Tonight 3 – 1.")
    private val winningFrame = FrameReceipt(7, "a", "Kevin", theirPhone = false, events = emptySet())
    private val empty = board(a = 0, b = 0, matchesA = 0, matchesB = 0, receipt = null, canUndo = false)

    @Test fun sessionLoading() = shoot("session-loading", landscape = true) { Board(SessionUiState()) }
    @Test fun sessionBoard() = shoot("session-board", landscape = true) { Board(board()) }
    @Test fun sessionPending() = shoot("session-pending", landscape = true) { Board(board(pending = true)) }
    @Test fun sessionOpenEnded() = shoot("session-open-ended", landscape = true) { Board(board(a = 7, b = 6, race = null, game = null)) }
    @Test fun sessionHillHill() = shoot("session-hill-hill", landscape = true) { Board(board(a = 4, b = 4)) }
    @Test fun sessionFirstFrame() = shoot("session-first-frame", landscape = true) { Board(empty) }
    @Test fun sessionQuickGame() = shoot("session-quick-game", landscape = true) { Board(board(names = "Kevin" to "Tom")) }
    @Test fun sessionNext() = shoot("session-next", landscape = true) { Board(board(running = false)) }
    @Test fun sessionNextNotice() = shootMessage("session-next-notice", landscape = true) {
        Board(board(running = false, notice = Notice(1, "Match 4 ended · no winner")))
    }
    @Test fun sessionWon() = shoot("session-won", landscape = true) {
        Board(board(a = 0, b = 0, matchesA = 3, matchesB = 1, justWon = won, receipt = winningFrame))
    }
    @Test fun sessionWonTagged() = shoot("session-won-tagged", landscape = true) {
        Board(board(a = 0, b = 0, matchesA = 3, matchesB = 1, justWon = won, receipt = winningFrame.copy(events = setOf(FrameEvent.BREAK_AND_RUN))))
    }
    @Test fun sessionWonChange() = shoot("session-won-change", landscape = true, steps = { tap("Change") }) {
        Board(board(a = 0, b = 0, matchesA = 3, matchesB = 1, justWon = won, receipt = winningFrame))
    }
    @Test fun sessionMessage() = shootMessage("session-message", landscape = true) {
        Board(board(notice = Notice(1, "Couldn't save to the server: you're offline", warning = true)))
    }

    // Full time, on both phones.
    private val fullTime = FullTime(
        venue = "The Crown",
        length = java.time.Duration.ofMinutes(182),
        winnerId = "a",
        headline = "Kevin takes the night 3\u00A0–\u00A01",
        summary = "Four matches, 26 frames. 2 break & runs, a golden break and a hill-hill decider.",
        allTime = 13 to 9,
        lastMatch = Match("m4", 4, MatchSettings(GameType.NINE_BALL, 5), Status.ENDED, mapOf("a" to 5, "b" to 4)),
    )
    @Test fun sessionFullTime() = shoot("session-full-time", landscape = true) {
        Board(board(a = 5, b = 4, matchesA = 3, matchesB = 1, running = false).copy(fullTime = fullTime))
    }
    @Test fun sessionFullTimeLevel() = shoot("session-full-time-level", landscape = true) {
        Board(
            board(a = 2, b = 5, matchesA = 1, matchesB = 1, running = false, names = "Kevin" to "Tom").copy(
                fullTime = fullTime.copy(
                    venue = null, length = java.time.Duration.ofMinutes(48), winnerId = null,
                    headline = "Level on the night, 1 – 1", summary = "Two matches, 12 frames.", allTime = null,
                ),
            ),
        )
    }

    // The last frame's receipt, in each of its states.
    @Test fun sessionReceiptTheirPhone() = shoot("session-receipt-their-phone", landscape = true) {
        Board(board(a = 3, b = 3, receipt = FrameReceipt(6, "b", "Julian", theirPhone = true, events = emptySet())))
    }
    @Test fun sessionReceiptTagged() = shoot("session-receipt-tagged", landscape = true) {
        Board(board(receipt = frame6.copy(events = setOf(FrameEvent.BREAK_AND_RUN))))
    }
    @Test fun sessionDouble() = shoot("session-double", landscape = true) {
        Board(board(a = 1, b = 2, receipt = FrameReceipt(3, "b", "Julian", theirPhone = true, events = emptySet(), double = DoubleFrame(2, 3))))
    }
    @Test fun sessionUndone() = shootMessage("session-undone", landscape = true) {
        Board(board(a = 3, notice = Notice(1, "Kevin undid frame 6 · 4 – 2 → 3 – 2")))
    }
    @Test fun sessionTags() = shoot("session-tags", landscape = true, steps = { compose.onNodeWithTag("receipt").performClick() }) {
        Board(board(receipt = frame6.copy(events = setOf(FrameEvent.BREAK_AND_RUN))))
    }

    // The match sheet behind ≡, and what it opens.
    @Test fun sessionSheet() = shoot("session-sheet", landscape = true, steps = { tapIcon("Game menu") }) { Board(board()) }
    @Test fun sessionSheetNewMatch() = shoot("session-sheet-new-match", landscape = true, steps = { tapIcon("Game menu") }) {
        Board(board(a = 0, b = 0))
    }
    @Test fun sessionSheetOpenEnded() = shoot("session-sheet-open-ended", landscape = true, steps = { tapIcon("Game menu") }) {
        Board(board(a = 7, b = 6, race = null, game = null))
    }
    @Test fun sessionEndMatch() = shoot("session-end-match", landscape = true, dialog = true, steps = {
        tapIcon("Game menu"); tap("End match")
    }) { Board(board()) }
    @Test fun sessionEndMatchOpenEnded() = shoot("session-end-match-open-ended", landscape = true, dialog = true, steps = {
        tapIcon("Game menu"); tap("End match")
    }) { Board(board(a = 7, b = 6, race = null, game = null)) }
    @Test fun sessionEndSession() = shoot("session-end-session", landscape = true, dialog = true, steps = {
        tapIcon("Game menu"); tap("End session")
    }) { Board(board()) }
    @Test fun sessionEndSessionEmpty() = shoot("session-end-session-empty", landscape = true, dialog = true, steps = {
        tapIcon("Game menu"); tap("End session")
    }) { Board(empty) }
    @Test fun sessionNextEndSession() = shoot("session-next-end-session", landscape = true, dialog = true, steps = {
        tapIcon("Game menu")
    }) { Board(board(running = false)) }

    // ---- Home ----

    private val julian = RivalCard(
        "r1", "Julian", myWins = 12, rivalWins = 9,
        nights = listOf(true, true, false, true, true, false, false, true, true, false),
    )

    /** Ten rivals, most recently played first, for how Home holds up with a full phone. */
    private val tenRivals = listOf(
        julian, RivalCard("r2", "Lara", 4, 10), RivalCard("r3", "Rosa", 15, 11), RivalCard("r4", "Tom", 12, 12),
        RivalCard("r5", "Maya", 7, 5), RivalCard("r6", "Sam", 9, 2), RivalCard("r7", "Nina", 5, 9),
        RivalCard("r8", "Priya", 3, 8), RivalCard("r9", "Oli", 1, 4), RivalCard("r10", "Dev", 2, 0),
    )

    private fun home(
        rivals: List<RivalCard> = listOf(julian),
        invites: List<InviteCard> = emptyList(),
        guestGames: List<GuestGame> = emptyList(),
        signedIn: Boolean = true,
        pending: Boolean = false,
        loading: Boolean = false,
        message: String? = null,
    ) = HomeUiState(
        loading = loading, signedIn = signedIn, myName = if (signedIn) "Kevin" else "",
        rivals = rivals, invites = invites, guestGames = guestGames, pendingSync = pending, message = message,
    )

    private fun guest(i: Int, active: Boolean = false) =
        GuestGame("g$i", active, start.minusSeconds(86_400L * i), GuestSide("a", "Kevin", 2 + i % 2), GuestSide("b", "Tom", 1 + i % 3))

    @Composable private fun Home(state: HomeUiState) = HomeContent(state, HomeActions())

    @Test fun homeLoading() = shoot("home-loading") { Home(home(loading = true)) }
    @Test fun homeIdle() = shoot("home-idle") { Home(home(guestGames = listOf(guest(1)))) }
    @Test fun homeLive() = shoot("home-live") {
        Home(home(rivals = listOf(julian.copy(activeSessionId = "s1", tonight = 2 to 1)), pending = true))
    }
    @Test fun homeInvites() = shoot("home-invites") {
        Home(home(invites = listOf(InviteCard("r2", "Sam", incoming = true), InviteCard("r3", "Alex", incoming = false))))
    }
    @Test fun homeQuickGameRunning() = shoot("home-quick-game-running") { Home(home(guestGames = listOf(guest(0, active = true), guest(1)))) }
    @Test fun homeGuestGamesAll() = shoot("home-guest-games-all", steps = { tap("See all 5") }) {
        Home(home(guestGames = (1..5).map { guest(it) }))
    }
    @Test fun homeNoRivals() = shoot("home-no-rivals") { Home(home(rivals = emptyList())) }
    @Test fun homeSignedOut() = shoot("home-signed-out") { Home(home(rivals = emptyList(), signedIn = false, guestGames = listOf(guest(1), guest(2)))) }
    @Test fun homeSignedOutQuickGameRunning() = shoot("home-signed-out-quick-game-running") {
        Home(home(rivals = emptyList(), signedIn = false, guestGames = listOf(guest(0, active = true))))
    }
    @Test fun homeMessage() = shootMessage("home-message") { Home(home(message = "Couldn't start the session: you're offline")) }
    @Test fun homeMenu() = shoot("home-menu", steps = { tapIcon("More") }) { Home(home()) }
    @Test fun homeManyRivals() = shoot("home-many-rivals") {
        Home(home(rivals = tenRivals, guestGames = listOf(guest(0, active = true)) + (1..25).map { guest(it) }))
    }
    @Test fun homeRivalsAll() = shoot("home-rivals-all", steps = { tap("See all 10 rivals") }) {
        Home(home(rivals = tenRivals, guestGames = (1..25).map { guest(it) }))
    }
    @Test fun homeQuickGame() = shoot("home-quick-game", steps = { tap("Quick game") }) { Home(home()) }
    @Test fun homeSaveGame() = shoot("home-save-game", steps = { tap("Save") }) {
        Home(home(rivals = listOf(julian, RivalCard("r2", "Sam", 7, 9)), guestGames = listOf(guest(1))))
    }

    // ---- Rivalry ----

    private val stats = Stats(
        "a", "b", Record(12, 9), Record(61, 55), NightsRecord(4, 2, 1),
        mapOf(
            GameType.NINE_BALL to GameTypeStats(Record(9, 5), Record(44, 35)),
            GameType.TEN_BALL to GameTypeStats(Record(3, 4), Record(17, 20)),
        ),
        Streak("b", 2),
        mapOf("a" to 5, "b" to 3),
        mapOf(FrameEvent.BREAK_AND_RUN to Count(3, 1), FrameEvent.GOLDEN_BREAK to Count(0, 1)),
    )

    private fun rivalry(
        loading: Boolean = false,
        accepted: Boolean = true,
        active: ActiveSessionSummary? = null,
        starting: Boolean = false,
        pending: Boolean = false,
        error: String? = null,
    ) = RivalryUiState(
        loading = loading, myName = "Kevin", rivalName = "Julian", accepted = accepted,
        myWins = if (accepted) 12 else 0, rivalWins = if (accepted) 9 else 0,
        activeSession = active, recentVenues = listOf("The Crown", "Rileys"),
        sessions = if (accepted) {
            listOf(
                SessionItem("s1", start, start.plusSeconds(10_800), "The Crown", 3, 2),
                SessionItem("s2", start.minusSeconds(7 * 86_400), start.minusSeconds(7 * 86_400 - 9_000), null, 1, 4),
            )
        } else {
            emptyList()
        },
        stats = stats.takeIf { accepted }, pendingSync = pending, starting = starting, error = error,
    )

    @Composable private fun Rivalry(state: RivalryUiState) = RivalryContent(state, { _, _ -> }, {}, {}, {}, {}, {})

    @Test fun rivalryLoading() = shoot("rivalry-loading") { Rivalry(rivalry(loading = true)) }
    @Test fun rivalryLive() = shoot("rivalry-live") { Rivalry(rivalry(active = ActiveSessionSummary("s9", 2, 1), pending = true)) }
    @Test fun rivalryNotAccepted() = shoot("rivalry-not-accepted") { Rivalry(rivalry(accepted = false)) }
    @Test fun rivalryStarting() = shoot("rivalry-starting") { Rivalry(rivalry(starting = true)) }
    @Test fun rivalryError() = shootMessage("rivalry-error") { Rivalry(rivalry(error = "Couldn't load the sessions: permission denied")) }
    @Test fun rivalryMenu() = shoot("rivalry-menu", steps = { tapIcon("More") }) { Rivalry(rivalry()) }
    @Test fun rivalryRemove() = shoot("rivalry-remove", steps = { tapIcon("More"); tap("Remove rival") }) { Rivalry(rivalry()) }
    @Test fun rivalryNewSession() = shoot("rivalry-new-session", steps = { tap("Start session") }) { Rivalry(rivalry()) }

    // ---- Session detail ----

    private fun detail(
        loading: Boolean = false,
        found: Boolean = true,
        matches: Boolean = true,
        rivalName: String = "Julian",
        long: Boolean = false,
    ): SessionDetailUiState {
        val br = FrameEvent.BREAK_AND_RUN
        fun frames(vararg w: String) = w.mapIndexed { i, x -> Frame("f$i", i + 1, x, recordedBy = "a", events = if (i == 0) setOf(br) else emptySet()) }
        fun match(n: Int, a: Int, b: Int, winner: String?) =
            Match("m$n", n, MatchSettings(GameType.NINE_BALL, 3), Status.ENDED, mapOf("a" to a, "b" to b), winner)
        return SessionDetailUiState(
            loading = loading,
            session = Session("s", listOf("a", "b"), Status.ENDED, start, start.plusSeconds(10_800), "The Crown", "a", mapOf("a" to 1, "b" to 1)),
            me = DetailPlayer("a", "Kevin", 1).takeIf { found },
            rival = DetailPlayer("b", rivalName, 1).takeIf { found },
            matches = if (matches) {
                listOf(
                    MatchWithFrames(match(1, 3, 1, "a"), frames("a", "a", "b", "a")),
                    MatchWithFrames(match(2, 2, 3, "b"), frames("a", "a", "b", "b", "b")),
                ) + if (long) {
                    // A long open-ended match, so the page scrolls far enough to fold the score away.
                    listOf(MatchWithFrames(match(3, 6, 8, "b"), frames(*"aaabbbabbbabba".map { "$it" }.toTypedArray())))
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            },
        )
    }

    @Composable private fun Detail(state: SessionDetailUiState) = SessionDetailContent(state, onBack = {}, onDelete = {})

    @Test fun detailLoading() = shoot("detail-loading") { Detail(detail(loading = true)) }
    @Test fun detailNotFound() = shoot("detail-not-found") { Detail(detail(found = false)) }
    @Test fun detailNoMatches() = shoot("detail-no-matches") { Detail(detail(matches = false)) }
    @Test fun detailQuickGame() = shoot("detail-quick-game") { Detail(detail(rivalName = "Tom")) }
    @Test fun detailQuickGameSave() = shoot("detail-quick-game-save") {
        SessionDetailContent(detail(rivalName = "Tom"), onBack = {}, onDelete = {}, onSave = {})
    }
    @Test fun detailScrolled() {
        setContent(landscape = false, phone = true) { Detail(detail(long = true)) }
        compose.onNodeWithTag(FRAME).performTouchInput { swipeUp(durationMillis = 400) }
        compose.waitForIdle()
        save("detail-scrolled", compose.onNodeWithTag(FRAME).captureToImage().asAndroidBitmap())
    }
    @Test fun detailMenu() = shoot("detail-menu", steps = { tapIcon("More") }) { Detail(detail()) }
    @Test fun detailDelete() = shoot("detail-delete", steps = { tapIcon("More"); tap("Delete session") }) { Detail(detail()) }

    // ---- Add a rival, invites, sign in ----

    @Composable private fun AddRival(state: AddRivalUiState) = AddRivalContent(state, {}, {}, {}, {}, {}, {})

    private val found = Player("b", "Julian Jones", "julian@example.com", null)

    @Test fun addRivalClosed() = shoot("add-rival-closed") { AddRival(AddRivalUiState()) }
    @Test fun addRivalFound() = shoot("add-rival-found") { AddRival(AddRivalUiState(searched = "julian@example.com", found = found)) }
    @Test fun addRivalNotFound() = shoot("add-rival-not-found") { AddRival(AddRivalUiState(searched = "julian@example.com")) }
    @Test fun addRivalInvited() = shoot("add-rival-invited") {
        AddRival(AddRivalUiState(searched = "julian@example.com", found = found, invited = true))
    }
    @Test fun addRivalLink() = shoot("add-rival-link", steps = { tap("Share a link") }) { AddRival(AddRivalUiState()) }
    @Test fun addRivalCode() = shoot("add-rival-code", steps = { tap("Enter a code") }) { AddRival(AddRivalUiState()) }
    @Test fun addRivalBusy() = shoot("add-rival-busy", steps = { tap("Share a link") }) { AddRival(AddRivalUiState(busy = true)) }

    @Composable private fun Invite(state: InviteUiState) = InviteContent(state, {}, {}, {}, {})

    private val invite = Invite("ABCD2345", "a", "Julian")

    @Test fun inviteSignedOut() = shoot("invite-signed-out") { Invite(InviteUiState(loading = false, invite = invite)) }
    @Test fun inviteSignedIn() = shoot("invite-signed-in") { Invite(InviteUiState(loading = false, invite = invite, signedIn = true)) }
    @Test fun inviteOwn() = shoot("invite-own") { Invite(InviteUiState(loading = false, invite = invite, signedIn = true, own = true)) }
    @Test fun inviteGone() = shoot("invite-gone") { Invite(InviteUiState(loading = false)) }
    @Test fun inviteLoading() = shoot("invite-loading") { Invite(InviteUiState()) }
    @Test fun inviteAccepting() = shoot("invite-accepting") { Invite(InviteUiState(loading = false, invite = invite, signedIn = true, accepting = true)) }
    @Test fun inviteError() = shoot("invite-error") { Invite(InviteUiState(loading = false, error = "Couldn't open the invite: you're offline")) }

    @Test fun signIn() = shoot("sign-in") { SignInContent(SignInUiState(), {}, {}, {}) }
    @Test fun signInBusy() = shoot("sign-in-busy") { SignInContent(SignInUiState(inProgress = true), {}, {}, {}) }
    @Test fun signInError() = shootMessage("sign-in-error") {
        SignInContent(SignInUiState(error = "Sign-in was cancelled"), {}, {}, {})
    }
}
