package com.kevinbevan.rivals.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kevinbevan.rivals.data.MatchDefaults
import com.kevinbevan.rivals.model.Invite
import com.kevinbevan.rivals.ui.invite.InviteContent
import com.kevinbevan.rivals.ui.invite.InviteUiState
import com.kevinbevan.rivals.ui.signin.SignInContent
import com.kevinbevan.rivals.ui.signin.SignInUiState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinbevan.rivals.domain.Count
import com.kevinbevan.rivals.domain.GameTypeStats
import com.kevinbevan.rivals.domain.NightsRecord
import com.kevinbevan.rivals.domain.Record
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.Streak
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.history.DetailPlayer
import com.kevinbevan.rivals.ui.history.SessionDetailContent
import com.kevinbevan.rivals.ui.history.SessionDetailUiState
import com.kevinbevan.rivals.model.Player
import com.kevinbevan.rivals.ui.home.GuestGame
import com.kevinbevan.rivals.ui.home.GuestSide
import com.kevinbevan.rivals.ui.home.HomeActions
import com.kevinbevan.rivals.ui.home.HomeContent
import com.kevinbevan.rivals.ui.home.HomeUiState
import com.kevinbevan.rivals.ui.home.InviteCard
import com.kevinbevan.rivals.ui.home.RivalCard
import com.kevinbevan.rivals.ui.invite.AddRivalContent
import com.kevinbevan.rivals.ui.invite.AddRivalUiState
import com.kevinbevan.rivals.ui.rivalry.RivalryContent
import com.kevinbevan.rivals.ui.rivalry.RivalryTab
import com.kevinbevan.rivals.ui.rivalry.RivalryUiState
import com.kevinbevan.rivals.ui.rivalry.SessionItem
import com.kevinbevan.rivals.ui.session.MatchResult
import com.kevinbevan.rivals.ui.session.PlayerSide
import com.kevinbevan.rivals.ui.session.SessionActions
import com.kevinbevan.rivals.ui.session.SessionContent
import com.kevinbevan.rivals.ui.session.SessionUiState
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not assertions: renders each screen with sample data, light and dark, to PNGs for a human
 * (or Claude) to look at. `scripts/emulator-tests.sh` pulls them into `app/build/screenshots`.
 */
@RunWith(AndroidJUnit4::class)
class Screenshots {
    @get:Rule val compose = createComposeRule()

    private companion object {
        const val FRAME = "screenshot-frame"
    }

    private fun shoot(name: String, dark: Boolean, landscape: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            Themed(dark) {
                if (landscape) {
                    // The scoreboard locks the phone to landscape; render it at a landscape phone's size.
                    // It also hides the system bars, so consume the emulator's insets: otherwise the
                    // render is padded by bars that aren't there in the app.
                    DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(915.dp, 412.dp))) {
                        Box(
                            Modifier
                                .testTag(FRAME)
                                .consumeWindowInsets(WindowInsets.safeDrawing),
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
        val node = if (landscape) compose.onNodeWithTag(FRAME) else compose.onRoot()
        save(name, dark, node.captureToImage().asAndroidBitmap())
    }

    /** A dialog lives in its own window, so it is captured by opening it and shooting that window. */
    private fun shootDialog(
        name: String,
        dark: Boolean,
        open: String,
        vararg then: String,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { Themed(dark) { content() } }
        compose.onNodeWithText(open).performClick()
        then.forEach { compose.onNodeWithText(it).performClick() }
        save(name, dark, compose.onNode(isDialog()).captureToImage().asAndroidBitmap())
    }

    /** The theme, with match settings of its own so a render never picks up another test's. */
    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        val defaults = remember { MatchDefaults() }
        CompositionLocalProvider(LocalMatchDefaults provides defaults) {
            RivalsTheme(darkTheme = dark) { content() }
        }
    }

    private fun save(name: String, dark: Boolean, bitmap: Bitmap) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        File(dir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val start = Instant.parse("2026-09-21T19:30:00Z")

    @Test fun homeLight() = shoot("home", dark = false) { Home() }
    @Test fun homeDark() = shoot("home", dark = true) { Home() }
    @Test fun homeNoRivalsDark() = shoot("home-no-rivals", dark = true) {
        HomeContent(HomeUiState(loading = false, signedIn = true, myName = "Kevin"), HomeActions())
    }
    @Test fun guestHomeLight() = shoot("home-guest", dark = false) { SignedOutHome() }
    @Test fun guestHomeDark() = shoot("home-guest", dark = true) { SignedOutHome() }
    @Test fun rivalryLight() = shoot("rivalry", dark = false) { Rivalry() }
    @Test fun rivalryDark() = shoot("rivalry", dark = true) { Rivalry() }
    @Test fun rivalryEmptyDark() = shoot("rivalry-empty", dark = true) { RivalryContent(RivalryUiState(loading = false, myName = "Kevin", rivalName = "Julian"), { _, _ -> }, {}, {}, {}, {}, {}) }
    @Test fun addRivalLight() = shoot("add-rival", dark = false) { AddRival() }
    @Test fun addRivalDark() = shoot("add-rival", dark = true) { AddRival() }
    @Test fun sessionLight() = shoot("session", dark = false, landscape = true) { SessionScreen() }
    @Test fun sessionDark() = shoot("session", dark = true, landscape = true) { SessionScreen() }
    @Test fun sessionBetweenMatchesDark() = shoot("session-next", dark = true, landscape = true) { SessionScreen(running = false) }
    @Test fun sessionMatchWonDark() = shoot("session-won", dark = true, landscape = true) { MatchWonScreen() }
    @Test fun signInDark() = shoot("sign-in", dark = true) { SignInContent(SignInUiState(), {}, {}, {}) }
    @Test fun inviteDark() = shoot("invite", dark = true) {
        InviteContent(InviteUiState(loading = false, invite = Invite("ABCD2345", "a", "Kevin")), {}, {}, {}, {})
    }
    @Test fun inviteGoneDark() = shoot("invite-gone", dark = true) {
        InviteContent(InviteUiState(loading = false, invite = null), {}, {}, {}, {})
    }
    @Test fun statsLight() = shoot("rivalry-stats", dark = false) { Rivalry(stats = true) }
    @Test fun statsDark() = shoot("rivalry-stats", dark = true) { Rivalry(stats = true) }
    @Test fun quickGameDark() = shootDialog("quick-game", dark = true, open = "Quick game") { SignedOutHome() }
    @Test fun quickGameLight() = shootDialog("quick-game", dark = false, open = "Quick game") { SignedOutHome() }
    @Test fun quickGameChosenDark() = shootDialog("quick-game-chosen", dark = true, open = "Quick game", "10-BALL") { SignedOutHome() }
    @Test fun detailLight() = shoot("detail", dark = false) { Detail() }
    @Test fun detailDark() = shoot("detail", dark = true) { Detail() }

    @Composable private fun Home() = HomeContent(
        HomeUiState(
            loading = false,
            signedIn = true,
            myName = "Kevin",
            rivals = listOf(
                RivalCard(
                    "r1", "Julian", myWins = 12, rivalWins = 9,
                    activeSessionId = "s1", tonight = 2 to 1,
                    nights = listOf(true, true, false, true, true, false, false, true, true, false),
                ),
            ),
            invites = listOf(InviteCard("r2", "Sam", incoming = true)),
            guestGames = listOf(GuestGame("g", active = false, startedAt = start, GuestSide("a", "Kevin", 2), GuestSide("b", "Tom", 1))),
        ),
        HomeActions(),
    )

    @Composable private fun SignedOutHome() = HomeContent(HomeUiState(loading = false), HomeActions())

    @Composable private fun Rivalry(stats: Boolean = false) = RivalryContent(
        RivalryUiState(
            loading = false,
            myName = "Kevin",
            rivalName = "Julian",
            myWins = 12,
            rivalWins = 9,
            sessions = listOf(
                SessionItem("s1", start, start.plusSeconds(10_800), "The Crown", 3, 2),
                SessionItem("s2", start.minusSeconds(7 * 86_400), start.minusSeconds(7 * 86_400 - 9_000), null, 1, 4),
                SessionItem("s3", start.minusSeconds(14 * 86_400), start.minusSeconds(14 * 86_400 - 7_200), "Rileys", 2, 2),
            ),
            stats = sampleStats,
        ),
        { _, _ -> }, {}, {}, {}, {}, {},
        initialTab = if (stats) RivalryTab.STATS else RivalryTab.SESSIONS,
    )

    private val sampleStats = Stats(
        "a", "b", Record(12, 9), Record(61, 55), NightsRecord(4, 2, 1),
        mapOf(
            GameType.NINE_BALL to GameTypeStats(Record(9, 5), Record(44, 35)),
            GameType.TEN_BALL to GameTypeStats(Record(3, 4), Record(17, 20)),
        ),
        Streak("b", 2),
        mapOf("a" to 5, "b" to 3),
        mapOf(FrameEvent.BREAK_AND_RUN to Count(3, 1), FrameEvent.GOLDEN_BREAK to Count(0, 1)),
    )

    @Composable private fun AddRival() = AddRivalContent(
        AddRivalUiState(searched = "julian@example.com", found = Player("b", "Julian Jones", "julian@example.com", null)),
        {}, {}, {}, {}, {}, {},
    )

    @Composable private fun SessionScreen(running: Boolean = true) = SessionContent(
        SessionUiState(
            loading = false,
            me = PlayerSide("a", "Kevin", frames = 4, matches = 2),
            rival = PlayerSide("b", "Julian", frames = 2, matches = 1),
            match = Match(
                "m", 4, MatchSettings(GameType.NINE_BALL, 5), Status.ACTIVE, mapOf("a" to 4, "b" to 2),
                startedAt = Instant.now().minusSeconds(754),
            ).takeIf { running },
            lastFrameEvents = emptySet(),
            canUndo = true,
            pendingSync = true,
        ),
        SessionActions(),
    )

    @Composable private fun MatchWonScreen() = SessionContent(
        SessionUiState(
            loading = false,
            me = PlayerSide("a", "Kevin", frames = 0, matches = 3),
            rival = PlayerSide("b", "Julian", frames = 0, matches = 1),
            match = Match(
                "m5", 5, MatchSettings(GameType.NINE_BALL, 5), Status.ACTIVE, emptyMap(),
                startedAt = Instant.now(),
            ),
            justWon = MatchResult("m4", 4, "9-ball", "a", "Kevin", 5, 2, "Match 5 starts now. Tonight 3 – 1."),
            canUndo = true,
        ),
        SessionActions(),
    )

    @Composable private fun Detail() {
        val br = FrameEvent.BREAK_AND_RUN
        val gb = FrameEvent.GOLDEN_BREAK
        val fouls = FrameEvent.THREE_FOULS
        fun frames(vararg w: Pair<String, Set<FrameEvent>>) =
            w.mapIndexed { i, (x, e) -> Frame("f$i", i + 1, x, recordedBy = "a", events = e) }
        fun p(w: String, vararg e: FrameEvent) = w to e.toSet()
        fun match(n: Int, a: Int, b: Int, winner: String?) =
            Match("m$n", n, MatchSettings(GameType.NINE_BALL, 3), Status.ENDED, mapOf("a" to a, "b" to b), winner)
        SessionDetailContent(
            SessionDetailUiState(
                loading = false,
                session = Session("s", listOf("a", "b"), Status.ENDED, start, start.plusSeconds(10_800), "The Crown", "a", mapOf("a" to 2, "b" to 1)),
                me = DetailPlayer("a", "Kevin", 2),
                rival = DetailPlayer("b", "Julian", 1),
                matches = listOf(
                    MatchWithFrames(match(1, 3, 1, "a"), frames(p("a", br), p("a", br), p("b"), p("a"))),
                    MatchWithFrames(match(2, 2, 3, "b"), frames(p("a"), p("a"), p("b", fouls), p("b"), p("b", gb))),
                    MatchWithFrames(match(3, 3, 0, "a"), frames(p("a"), p("a", gb), p("a"))),
                ),
            ),
            onBack = {}, onDelete = {},
        )
    }
}
