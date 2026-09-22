package com.kevinbevan.rivals.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kevinbevan.rivals.domain.BreakRecord
import com.kevinbevan.rivals.domain.GameTypeStats
import com.kevinbevan.rivals.domain.NightsRecord
import com.kevinbevan.rivals.domain.Record
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.Streak
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.history.DetailPlayer
import com.kevinbevan.rivals.ui.history.SessionDetailContent
import com.kevinbevan.rivals.ui.history.SessionDetailUiState
import com.kevinbevan.rivals.ui.home.HomeContent
import com.kevinbevan.rivals.ui.home.HomeUiState
import com.kevinbevan.rivals.ui.session.PlayerSide
import com.kevinbevan.rivals.ui.session.SessionActions
import com.kevinbevan.rivals.ui.session.SessionContent
import com.kevinbevan.rivals.ui.session.SessionUiState
import com.kevinbevan.rivals.ui.stats.StatsContent
import com.kevinbevan.rivals.ui.stats.StatsUiState
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

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { RivalsTheme(darkTheme = dark) { content() } }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        File(dir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val start = Instant.parse("2026-09-21T19:30:00Z")

    @Test fun homeLight() = shoot("home", dark = false) { Home() }
    @Test fun homeDark() = shoot("home", dark = true) { Home() }
    @Test fun sessionLight() = shoot("session", dark = false) { SessionScreen() }
    @Test fun sessionDark() = shoot("session", dark = true) { SessionScreen() }
    @Test fun statsLight() = shoot("stats", dark = false) { StatsScreen() }
    @Test fun statsDark() = shoot("stats", dark = true) { StatsScreen() }
    @Test fun detailLight() = shoot("detail", dark = false) { Detail() }
    @Test fun detailDark() = shoot("detail", dark = true) { Detail() }

    @Composable private fun Home() = HomeContent(
        HomeUiState(loading = false, myName = "Kevin", rivalId = "b", rivalName = "Julian", myWins = 12, rivalWins = 9),
        { _, _ -> }, {}, {}, {}, {}, {},
    )

    @Composable private fun SessionScreen() = SessionContent(
        SessionUiState(
            loading = false,
            me = PlayerSide("a", "Kevin", frames = 4, matches = 2),
            rival = PlayerSide("b", "Julian", frames = 2, matches = 1),
            match = Match("m", 4, MatchSettings(GameType.EIGHT_BALL, 5), Status.ACTIVE, mapOf("a" to 4, "b" to 2)),
            frameWinners = listOf("Kevin", "Julian", "Kevin", "Kevin", "Julian", "Kevin"),
            breakerId = "b",
            canUndo = true,
            pendingSync = true,
        ),
        SessionActions(),
    )

    @Composable private fun StatsScreen() = StatsContent(
        StatsUiState(
            loading = false, myName = "Kevin", rivalName = "Julian",
            stats = Stats(
                "a", "b", Record(12, 9), Record(61, 55), NightsRecord(4, 2, 1),
                mapOf(
                    GameType.EIGHT_BALL to GameTypeStats(Record(9, 5), Record(44, 35)),
                    GameType.NINE_BALL to GameTypeStats(Record(3, 4), Record(17, 20)),
                ),
                Streak("b", 2), mapOf("a" to 5, "b" to 3), BreakRecord(40, 26), BreakRecord(38, 21),
            ),
        ),
        onBack = {},
    )

    @Composable private fun Detail() {
        fun frames(vararg w: String) = w.mapIndexed { i, x -> Frame("f$i", i + 1, x, recordedBy = "a") }
        fun match(n: Int, a: Int, b: Int, winner: String?) =
            Match("m$n", n, MatchSettings(GameType.EIGHT_BALL, 3), Status.ENDED, mapOf("a" to a, "b" to b), winner)
        SessionDetailContent(
            SessionDetailUiState(
                loading = false,
                session = Session("s", listOf("a", "b"), Status.ENDED, start, start.plusSeconds(10_800), "The Crown", "a", mapOf("a" to 1, "b" to 1)),
                me = DetailPlayer("a", "Kevin", 1),
                rival = DetailPlayer("b", "Julian", 1),
                matches = listOf(
                    MatchWithFrames(match(1, 3, 1, "a"), frames("a", "b", "a", "a")),
                    MatchWithFrames(match(2, 2, 3, "b"), frames("b", "a", "b", "a", "b")),
                ),
            ),
            onBack = {}, onDelete = {},
        )
    }
}
