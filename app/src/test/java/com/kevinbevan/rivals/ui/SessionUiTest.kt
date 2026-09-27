package com.kevinbevan.rivals.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.Match
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.model.Status
import com.kevinbevan.rivals.ui.session.DoubleFrame
import com.kevinbevan.rivals.ui.session.FrameReceipt
import com.kevinbevan.rivals.ui.session.FullTime
import com.kevinbevan.rivals.ui.session.PlayedMatch
import com.kevinbevan.rivals.ui.session.MatchResult
import com.kevinbevan.rivals.ui.session.Notice
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import com.kevinbevan.rivals.ui.session.PlayerSide
import com.kevinbevan.rivals.ui.session.SessionActions
import com.kevinbevan.rivals.ui.session.SessionContent
import com.kevinbevan.rivals.ui.session.SessionUiState
import com.kevinbevan.rivals.ui.session.formatElapsed
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A node whose click is labelled [label] (the steppers carry theirs as a click label). */
private fun hasClickLabel(label: String) = SemanticsMatcher("click label $label") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}

// The scoreboard is landscape-only in the app, so it's tested on a phone turned sideways.
@Config(qualifiers = "+land")
@RunWith(AndroidJUnit4::class)
class SessionUiTest {
    @get:Rule val compose = createComposeRule()

    private val race5 = MatchSettings(GameType.NINE_BALL, raceTo = 5)

    private fun match(a: Int, b: Int) = Match("m", 2, race5, Status.ACTIVE, mapOf("a" to a, "b" to b))

    private fun state(a: Int = 3, b: Int = 1, matchesA: Int = 1, matchesB: Int = 0) = SessionUiState(
        loading = false,
        me = PlayerSide("a", "Kevin", frames = a, matches = matchesA),
        rival = PlayerSide("b", "Julian", frames = b, matches = matchesB),
        match = match(a, b),
        lastFrame = if (a + b > 0) FrameReceipt(a + b, "a", "Kevin", theirPhone = false, events = emptySet()) else null,
        canUndo = a + b > 0 || matchesA + matchesB > 0,
    )

    private fun show(state: SessionUiState, actions: SessionActions = SessionActions()) =
        compose.setContent { Fixture { SessionContent(state, actions) } }

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
        // The status line is one label along the foot of the board, and labels are uppercase.
        compose.onNodeWithText("MATCH 2 · 9-BALL · RACE TO 5").assertIsDisplayed()
        compose.onNodeWithTag("tonight").assertTextEquals("1 – 0")
        compose.onNodeWithText("ON THE HILL").assertIsDisplayed()
    }

    @Test
    fun aWonMatchDimsTheBoardAndNamesTheWinner() {
        var undone = false
        show(
            state(a = 0, b = 0).copy(
                justWon = MatchResult(
                    matchId = "m1",
                    number = 1,
                    gameLabel = "9-ball",
                    winnerId = "a",
                    winnerName = "Kevin",
                    winnerFrames = 5,
                    loserFrames = 2,
                    next = "Match 2 starts now. Session 1 – 0.",
                ),
                canUndo = true,
            ),
            SessionActions(onUndo = { undone = true }),
        )
        compose.onNodeWithText("Kevin takes it 5 – 2").assertIsDisplayed()
        compose.onNodeWithText("Match 2 starts now. Session 1 – 0.").assertIsDisplayed()
        // The halves stop taking taps while the result is up.
        compose.onNodeWithTag("score-a").assertIsNotEnabled()
        compose.onNodeWithText("Undo").performClick()
        assertTrue(undone)
    }

    @Test
    fun aWonMatchIsClosedByPlayOnOrByTappingTheBoard() {
        var dismissed = 0
        val won = state(a = 0, b = 0).copy(
            justWon = MatchResult(
                matchId = "m1",
                number = 1,
                gameLabel = "9-ball",
                winnerId = "a",
                winnerName = "Kevin",
                winnerFrames = 5,
                loserFrames = 2,
                next = "Match 2 starts now. Session 1 – 0.",
            ),
            canUndo = true,
        )
        show(won, SessionActions(onDismissResult = { dismissed++ }))
        compose.onNodeWithText("Play on").performClick()
        assertEquals(1, dismissed)
        // The dimmed board behind the panel closes it too, rather than doing nothing.
        compose.onNodeWithTag("result-backdrop").performTouchInput { click(Offset(10f, 10f)) }
        assertEquals(2, dismissed)
    }

    @Test
    fun theReceiptNamesTheLastFrameAndUndoesItInOneTap() {
        var undone = 0
        show(
            state(a = 3, b = 3).copy(lastFrame = FrameReceipt(6, "b", "Julian", theirPhone = true, events = emptySet())),
            SessionActions(onUndo = { undone++ }),
        )
        compose.onNodeWithText("RACK 6 · JULIAN · THEIR PHONE").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo rack 6").performClick()
        assertEquals(1, undone)
    }

    @Test
    fun aDoubleFrameIsCalledOut() {
        show(state(a = 1, b = 2).copy(lastFrame = FrameReceipt(3, "b", "Julian", true, emptySet(), DoubleFrame(2, 4))))
        compose.onNodeWithText("RACKS 2 & 3 · 4 S APART").assertIsDisplayed()
    }

    @Test
    fun aNoticeTakesTheReceiptsPlaceThenGoes() {
        compose.mainClock.autoAdvance = false
        val dismissed = mutableListOf<Notice>()
        val notice = Notice(1, "Julian undid rack 6 · 4 – 2 → 3 – 2")
        show(state().copy(notice = notice), SessionActions(onDismissNotice = { dismissed += it }))
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("JULIAN UNDID RACK 6 · 4 – 2 → 3 – 2").assertIsDisplayed()
        compose.onNodeWithTag("receipt").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals(listOf(notice), dismissed)
    }

    @Test
    fun tappingTheReceiptTagsTheLastFrame() {
        val toggled = mutableListOf<FrameEvent>()
        show(
            state().copy(lastFrame = FrameReceipt(4, "a", "Kevin", false, setOf(FrameEvent.GOLDEN_BREAK))),
            SessionActions(onToggleEvent = { toggled += it }),
        )
        compose.onNodeWithText("RACK 4 · KEVIN · GOLDEN BREAK").performClick()
        compose.onNodeWithText("GOLDEN BREAK").assertIsSelected()
        compose.onNodeWithText("BREAK & RUN").performClick()
        assertEquals(listOf(FrameEvent.BREAK_AND_RUN), toggled)
        // Choosing a tag closes the row.
        compose.onNodeWithTag("tags").assertDoesNotExist()
        // A tap off the tags closes them too, rather than recording a frame.
        compose.onNodeWithTag("receipt").performClick()
        compose.onNodeWithTag("tags").assertIsDisplayed()
        compose.onNodeWithTag("tags-backdrop").performClick()
        compose.onNodeWithTag("tags").assertDoesNotExist()
    }

    @Test
    fun withNothingPlayedThereIsNothingToTagOrUndo() {
        show(state(a = 0, b = 0, matchesA = 0))
        compose.onNodeWithTag("receipt").assertDoesNotExist()
        compose.onNodeWithTag("undo").assertDoesNotExist()
    }

    @Test
    fun theWinningFrameIsTaggedAndTheNextMatchSetInTheMatchWonPanel() {
        val toggled = mutableListOf<FrameEvent>()
        val changed = mutableListOf<MatchSettings>()
        var dismissed = 0
        show(
            state(a = 0, b = 0).copy(
                justWon = MatchResult("m1", 1, "9-ball", "a", "Kevin", 5, 2, "Match 2 starts now. Session 1 – 0."),
                lastFrame = FrameReceipt(7, "a", "Kevin", false, emptySet()),
            ),
            SessionActions(onToggleEvent = { toggled += it }, onChangeSettings = { changed += it }, onDismissResult = { dismissed++ }),
        )
        compose.onNodeWithText("9-ball · race to 5").assertIsDisplayed()
        compose.onNodeWithText("WON ON THREE FOULS").performClick()
        assertEquals(listOf(FrameEvent.THREE_FOULS), toggled)
        compose.onNodeWithText("Change").performClick()
        compose.onNode(hasClickLabel("One more rack")).performClick()
        assertEquals(listOf(MatchSettings(GameType.NINE_BALL, raceTo = 6)), changed)
        // Touched, the panel waits for Play on: the dimmed board no longer closes it.
        compose.onNodeWithTag("result-backdrop").performTouchInput { click(Offset(10f, 10f)) }
        assertEquals(0, dismissed)
        compose.onNodeWithText("Play on").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun theMatchClockShowsTimeSinceTheMatchStarted() {
        assertEquals("0:00", formatElapsed(-5))
        assertEquals("4:07", formatElapsed(247))
        assertEquals("1:02:03", formatElapsed(3723))
    }

    @Test
    fun theMatchSheetChangesTheRaceMidMatchButNotBelowTheLeader() {
        val changed = mutableListOf<MatchSettings>()
        show(state(a = 4, b = 2), SessionActions(onChangeSettings = { changed += it }))
        compose.onNodeWithContentDescription("Game menu").performClick()
        compose.onNodeWithTag("match-sheet").assertIsDisplayed()
        compose.onNodeWithText("The race can't go below 5.", substring = true).assertIsDisplayed()
        compose.onNode(hasClickLabel("One fewer rack")).assertIsNotEnabled()
        compose.onNode(hasClickLabel("One more rack")).performClick()
        compose.onNodeWithText("10-BALL").performClick()
        assertEquals(
            listOf(MatchSettings(GameType.NINE_BALL, raceTo = 6), MatchSettings(GameType.TEN_BALL, raceTo = 6)),
            changed,
        )
        compose.onNodeWithTag("race-to").assertTextEquals("6")
        // A tap on the dimmed board closes the sheet.
        compose.onNodeWithTag("sheet-backdrop").performTouchInput { click(centerLeft + Offset(10f, 0f)) }
        compose.onNodeWithTag("match-sheet").assertDoesNotExist()
    }

    @Test
    fun beforeTheFirstFrameTheSheetHasNoEndMatch() {
        show(state(a = 0, b = 0))
        compose.onNodeWithContentDescription("Game menu").performClick()
        compose.onNodeWithText("End match").assertDoesNotExist()
        compose.onNodeWithText("End session").assertIsDisplayed()
    }

    @Test
    fun endingARaceEarlyWarnsItWontCount() {
        var ended = false
        show(state(a = 3, b = 1), SessionActions(onEndMatch = { ended = true }))
        compose.onNodeWithContentDescription("Game menu").performClick()
        compose.onNodeWithText("End match").performClick()
        compose.onNodeWithText("won't count", substring = true).assertIsDisplayed()
        compose.onNodeWithText("End match").performClick()
        assertTrue(ended)
    }

    @Test
    fun endingANightWithNothingPlayedSaysItWillBeDeleted() {
        var ended = false
        show(state(a = 0, b = 0, matchesA = 0), SessionActions(onEndSession = { ended = true }))
        compose.onNodeWithContentDescription("Game menu").performClick()
        compose.onNodeWithText("End session").performClick()
        compose.onNodeWithText("will be deleted", substring = true).assertIsDisplayed()
        compose.onNodeWithText("End session").performClick()
        assertTrue(ended)
    }

    @Test
    fun betweenMatchesTheNextOneCanBeStarted() {
        var started: MatchSettings? = null
        show(
            state().copy(
                match = null,
                lastSettings = race5,
                nextMatchNumber = 2,
                playedMatches = listOf(PlayedMatch(1, race5, "a", "Kevin", 3 to 2, endedEarly = true)),
            ),
            SessionActions(onStartMatch = { started = it }),
        )
        compose.onNodeWithTag("score-a").assertDoesNotExist()
        // Tonight's score is on the panel, not repeated on the top line; no frame receipt to tag.
        compose.onNodeWithTag("tonight-a").assertTextEquals("1")
        compose.onNodeWithTag("tonight").assertDoesNotExist()
        compose.onNodeWithTag("receipt").assertDoesNotExist()
        compose.onNodeWithText("race to 5 · ended early", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Start match 2").performClick()
        assertEquals(race5, started)
        // Home and End session are on the status line, as on the board.
        compose.onNodeWithContentDescription("Back to home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Game menu").performClick()
        compose.onNodeWithText("End this session?").assertIsDisplayed()
    }

    @Test
    fun fullTimeTellsTheNightAndLeavesForHomeOrItsDetail() {
        var done = 0
        var seeNight = 0
        show(
            state(a = 5, b = 4, matchesA = 3, matchesB = 1).copy(
                match = null,
                fullTime = FullTime(
                    venue = "The Crown", length = java.time.Duration.ofMinutes(182), winnerId = "a",
                    headline = "Kevin wins the session 3 – 1", summary = "Four matches, 26 racks.",
                    allTime = 13 to 9, lastMatch = null,
                ),
            ),
            SessionActions(onBack = { done++ }, onSeeNight = { seeNight++ }),
        )
        compose.onNodeWithText("FINAL · THE CROWN · 3 H 02 M").assertIsDisplayed()
        compose.onNodeWithText("Kevin wins the session 3 – 1").assertIsDisplayed()
        compose.onNodeWithText("13 – 9").assertIsDisplayed()
        // The board behind it no longer takes frames.
        compose.onNodeWithTag("score-a").assertDoesNotExist()
        compose.onNodeWithText("See the session").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(1 to 1, done to seeNight)
    }
}
