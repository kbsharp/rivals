package com.kevinbevan.rivals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.domain.Count
import com.kevinbevan.rivals.domain.GameTypeStats
import com.kevinbevan.rivals.domain.NightsRecord
import com.kevinbevan.rivals.domain.Record
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.Streak
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.rivalry.ActiveSessionSummary
import com.kevinbevan.rivals.ui.rivalry.RivalryContent
import com.kevinbevan.rivals.ui.rivalry.RivalryUiState
import com.kevinbevan.rivals.ui.rivalry.SessionItem
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RivalryUiTest {
    @get:Rule val compose = createComposeRule()

    private val ready = RivalryUiState(loading = false, myName = "Kevin", rivalName = "Julian")

    private val start = Instant.parse("2026-09-21T19:30:00Z")

    private val stats = Stats(
        myId = "a",
        rivalId = "b",
        matches = Record(12, 9),
        frames = Record(61, 55),
        nights = NightsRecord(4, 2, 1),
        byGameType = mapOf(GameType.EIGHT_BALL to GameTypeStats(Record(9, 5), Record(44, 35))),
        currentStreak = Streak("b", 2),
        longestStreaks = mapOf("a" to 5, "b" to 3),
        specials = mapOf(
            FrameEvent.BREAK_AND_RUN to Count(3, 1),
            FrameEvent.GOLDEN_BREAK to Count(0, 1),
        ),
    )

    private fun show(
        state: RivalryUiState,
        onStart: (MatchSettings, String?) -> Unit = { _, _ -> },
        onResume: (String) -> Unit = {},
        onOpenSessionDetail: (String) -> Unit = {},
    ) = compose.setContent {
        RivalsTheme { RivalryContent(state, onStart, onResume, onOpenSessionDetail, {}, {}, {}) }
    }

    @Test
    fun untilTheRivalAcceptsStartIsDisabledAndExplained() {
        show(ready.copy(accepted = false))
        compose.onNodeWithText("Start session").assertIsNotEnabled()
        compose.onNodeWithText("Julian needs to accept", substring = true).assertIsDisplayed()
    }

    @Test
    fun startingPassesTheChosenSettingsAndVenue() {
        var started: Pair<MatchSettings, String?>? = null
        show(ready.copy(recentVenues = listOf("The Crown")), onStart = { s, v -> started = s to v })

        compose.onNodeWithText("Start session").performClick()
        compose.onNodeWithText("9-BALL").performClick()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("THE CROWN").performClick()
        compose.onNodeWithText("Start").performClick()

        assertEquals(MatchSettings(GameType.NINE_BALL, raceTo = 6) to "The Crown", started)
    }

    @Test
    fun anActiveSessionIsResumedNotRestarted() {
        var resumed: String? = null
        show(ready.copy(activeSession = ActiveSessionSummary("s1", 2, 1)), onResume = { resumed = it })

        compose.onNodeWithText("Start session").assertDoesNotExist()
        compose.onNodeWithText("TONIGHT").assertIsDisplayed()
        compose.onNodeWithText("2 – 1").assertIsDisplayed()
        compose.onNodeWithText("Resume session").performClick()
        assertEquals("s1", resumed)
    }

    @Test
    fun theHeadToHeadIsShown() {
        show(ready.copy(myWins = 12, rivalWins = 9))
        compose.onNodeWithText("12 – 9").assertIsDisplayed()
        compose.onNodeWithText("YOU").assertIsDisplayed()
        compose.onNodeWithText("JULIAN").assertIsDisplayed()
    }

    @Test
    fun theSessionsTabListsNightsAndOpensOne() {
        var opened: String? = null
        show(
            ready.copy(
                sessions = listOf(
                    SessionItem("s1", start, start.plusSeconds(10_800), "The Crown", 3, 2),
                    SessionItem("s2", start.minusSeconds(86_400), start.minusSeconds(79_200), null, 1, 4),
                ),
            ),
            onOpenSessionDetail = { opened = it },
        )
        compose.onNodeWithText("The Crown · 3h").assertIsDisplayed()
        compose.onNodeWithText("YOU WON").assertIsDisplayed()
        compose.onNodeWithText("JULIAN WON").assertIsDisplayed()
        compose.onNodeWithText("3 – 2").performClick()
        assertEquals("s1", opened)
    }

    @Test
    fun withNoNightsTheSessionsTabSaysSo() {
        show(ready)
        compose.onNodeWithText("No nights yet").assertIsDisplayed()
    }

    @Test
    fun theStatsTabMirrorsBothSides() {
        show(ready.copy(stats = stats))
        compose.onNodeWithText("STATS").performClick()
        // MATCHES and FRAMES appear again under each game type, so take the first of each.
        compose.onAllNodesWithText("MATCHES").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("FRAMES").onFirst().assertIsDisplayed()
        compose.onNodeWithText("LONGEST RUN").assertIsDisplayed()
        compose.onNodeWithText("BREAK & RUN").assertIsDisplayed()
        compose.onNodeWithText("1 drawn").assertIsDisplayed()
        compose.onNodeWithText("8-BALL").assertIsDisplayed()
    }

    @Test
    fun withNothingPlayedTheStatsTabSaysSo() {
        show(ready.copy(stats = stats.copy(frames = Record(0, 0))))
        compose.onNodeWithText("STATS").performClick()
        compose.onNodeWithText("No frames played yet").assertIsDisplayed()
    }

    @Test
    fun theCurrentStreakIsNamedUnderTheScore() {
        show(ready.copy(myWins = 12, rivalWins = 9, stats = stats))
        compose.onNodeWithText("Julian has won the last 2").assertIsDisplayed()
    }
}
