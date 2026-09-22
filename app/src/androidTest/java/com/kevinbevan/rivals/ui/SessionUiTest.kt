package com.kevinbevan.rivals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.session.PlayerSide
import com.kevinbevan.rivals.ui.session.SessionActions
import com.kevinbevan.rivals.ui.session.SessionContent
import com.kevinbevan.rivals.ui.session.SessionUiState
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionUiTest {
    @get:Rule val compose = createComposeRule()

    private val race5 = MatchSettings(GameType.EIGHT_BALL, raceTo = 5)

    private fun match(a: Int, b: Int) = Match("m", 2, race5, Status.ACTIVE, mapOf("a" to a, "b" to b))

    private fun state(a: Int = 3, b: Int = 1, matchesA: Int = 1, matchesB: Int = 0) = SessionUiState(
        loading = false,
        me = PlayerSide("a", "Kevin", frames = a, matches = matchesA),
        rival = PlayerSide("b", "Julian", frames = b, matches = matchesB),
        match = match(a, b),
        breakerId = "a",
        canUndo = a + b > 0 || matchesA + matchesB > 0,
    )

    private fun show(state: SessionUiState, actions: SessionActions = SessionActions()) =
        compose.setContent { RivalsTheme { SessionContent(state, actions) } }

    @Test
    fun tappingAPlayerRecordsTheFrameForThem() {
        val recorded = mutableListOf<String>()
        show(state(), SessionActions(onRecordFrame = { recorded += it }))
        compose.onNodeWithTag("score-b").performClick()
        compose.onNodeWithTag("score-a").performClick()
        assertEquals(listOf("b", "a"), recorded)
    }

    @Test
    fun theScoreRaceAndHillAreShown() {
        show(state(a = 4, b = 2))
        compose.onNodeWithText("Match 2").assertIsDisplayed()
        compose.onNodeWithText("8-ball · race to 5").assertIsDisplayed()
        compose.onNodeWithText("on the hill").assertIsDisplayed()
    }

    @Test
    fun theBreakerCanBeChanged() {
        var chosen: String? = null
        show(state(), SessionActions(onChooseBreaker = { chosen = it }))
        compose.onNodeWithTag("breaker-a").assertIsSelected()
        compose.onNodeWithTag("breaker-b").performClick()
        assertEquals("b", chosen)
    }

    @Test
    fun undoIsOffWithNothingToUndo() {
        show(state(a = 0, b = 0, matchesA = 0))
        compose.onNodeWithText("Undo last frame").assertIsNotEnabled()
    }

    @Test
    fun beforeTheFirstFrameTheGameCanBeChangedButNotEnded() {
        show(state(a = 0, b = 0))
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Change game").assertIsDisplayed()
        compose.onNodeWithText("End match").assertDoesNotExist()
    }

    @Test
    fun endingARaceEarlyWarnsItWontCount() {
        var ended = false
        show(state(a = 3, b = 1), SessionActions(onEndMatch = { ended = true }))
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Change game").assertDoesNotExist()
        compose.onNodeWithText("End match").performClick()
        compose.onNodeWithText("won't count", substring = true).assertIsDisplayed()
        compose.onNodeWithText("End match").performClick()
        assertTrue(ended)
    }

    @Test
    fun endingANightWithNothingPlayedSaysItWillBeDeleted() {
        var ended = false
        show(state(a = 0, b = 0, matchesA = 0), SessionActions(onEndSession = { ended = true }))
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("End session").performClick()
        compose.onNodeWithText("will be deleted", substring = true).assertIsDisplayed()
        compose.onNodeWithText("End session").performClick()
        assertTrue(ended)
    }

    @Test
    fun betweenMatchesTheNextOneCanBeStarted() {
        var started: MatchSettings? = null
        show(state().copy(match = null, lastSettings = race5), SessionActions(onStartMatch = { started = it }))
        compose.onNodeWithTag("score-a").assertDoesNotExist()
        compose.onNodeWithText("Start match").performClick()
        assertEquals(race5, started)
    }
}
