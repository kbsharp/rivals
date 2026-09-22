package com.kevinbevan.rivals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.domain.BreakRecord
import com.kevinbevan.rivals.domain.NightsRecord
import com.kevinbevan.rivals.domain.Record
import com.kevinbevan.rivals.domain.Stats
import com.kevinbevan.rivals.domain.Streak
import com.kevinbevan.rivals.ui.stats.StatsContent
import com.kevinbevan.rivals.ui.stats.StatsUiState
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(state: StatsUiState) = compose.setContent { RivalsTheme { StatsContent(state, {}) } }

    private val stats = Stats(
        myId = "a",
        rivalId = "b",
        matches = Record(3, 1),
        frames = Record(10, 7),
        nights = NightsRecord(2, 1, 0),
        byGameType = emptyMap(),
        currentStreak = Streak("b", 1),
        longestStreaks = mapOf("a" to 3, "b" to 1),
        myBreaks = BreakRecord(4, 3),
        rivalBreaks = BreakRecord(0, 0),
    )

    @Test
    fun beforeAnyFramesItSaysSo() {
        show(StatsUiState(loading = false, myName = "Kevin", rivalName = "Julian", stats = stats.copy(frames = Record())))
        compose.onNodeWithText("No frames played yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun recordsStreaksAndBreaksReadFromMySide() {
        show(StatsUiState(loading = false, myName = "Kevin", rivalName = "Julian", stats = stats))
        compose.onNodeWithText("Kevin 3").assertIsDisplayed()
        compose.onNodeWithText("75%").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kevin 3, Julian 1").assertIsDisplayed()
        compose.onNodeWithText("Kevin won 2, lost 1").assertIsDisplayed()
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("Julian won the last match"))
        list.performScrollToNode(hasText("75% (3 of 4)"))
        compose.onNodeWithText("no breaks").assertIsDisplayed()
    }
}
