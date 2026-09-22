package com.kevinbevan.rivals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.model.Frame
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.MatchWithFrames
import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.history.DetailPlayer
import com.kevinbevan.rivals.ui.history.HistoryContent
import com.kevinbevan.rivals.ui.history.HistoryItem
import com.kevinbevan.rivals.ui.history.HistoryUiState
import com.kevinbevan.rivals.ui.history.SessionDetailContent
import com.kevinbevan.rivals.ui.history.SessionDetailUiState
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    @get:Rule val compose = createComposeRule()

    private val start = Instant.parse("2026-09-21T19:30:00Z")

    @Test
    fun anEmptyHistorySaysSo() {
        compose.setContent { RivalsTheme { HistoryContent(HistoryUiState(loading = false), {}, {}) } }
        compose.onNodeWithText("No finished sessions yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingASessionOpensIt() {
        var opened: String? = null
        val state = HistoryUiState(
            loading = false,
            myName = "Kevin",
            rivalName = "Julian",
            items = listOf(HistoryItem("s1", start, start.plusSeconds(7200), "The Crown", 1, 3)),
        )
        compose.setContent { RivalsTheme { HistoryContent(state, { opened = it }, {}) } }
        compose.onNodeWithText("Julian won").assertIsDisplayed()
        compose.onNodeWithText("1 – 3").performClick()
        assertEquals("s1", opened)
    }

    private val detail = SessionDetailUiState(
        loading = false,
        session = Session(
            "s1", listOf("a", "b"), Status.ENDED, start, start.plusSeconds(7200),
            venue = "The Crown", createdBy = "a", matchWins = mapOf("a" to 1, "b" to 0),
        ),
        me = DetailPlayer("a", "Kevin", 1),
        rival = DetailPlayer("b", "Julian", 0),
        matches = listOf(
            MatchWithFrames(
                Match(
                    "m1", 1, MatchSettings(GameType.EIGHT_BALL, 2), Status.ENDED,
                    mapOf("a" to 2, "b" to 1), winnerId = "a",
                ),
                listOf(Frame("f1", 1, "a", recordedBy = "a"), Frame("f2", 2, "b", recordedBy = "a"), Frame("f3", 3, "a", recordedBy = "a")),
            ),
        ),
    )

    @Test
    fun theDetailShowsEachFrameAndItsWinner() {
        compose.setContent { RivalsTheme { SessionDetailContent(detail, {}, {}) } }
        compose.onNodeWithText("Match 1").assertIsDisplayed()
        compose.onNodeWithText("Kevin won").assertIsDisplayed()
        compose.onNodeWithContentDescription("Frame 2 to Julian").assertIsDisplayed()
        compose.onNodeWithContentDescription("Frame 3 to Kevin").assertIsDisplayed()
    }

    @Test
    fun deletingASessionAsksFirst() {
        var deleted = false
        compose.setContent { RivalsTheme { SessionDetailContent(detail, {}, { deleted = true }) } }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("can't be undone", substring = true).assertIsDisplayed()
        assertTrue(!deleted)
        compose.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertTrue(deleted)
    }
}
