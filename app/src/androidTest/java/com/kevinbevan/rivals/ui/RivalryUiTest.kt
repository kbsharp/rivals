package com.kevinbevan.rivals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.rivalry.ActiveSessionSummary
import com.kevinbevan.rivals.ui.rivalry.RivalryContent
import com.kevinbevan.rivals.ui.rivalry.RivalryUiState
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RivalryUiTest {
    @get:Rule val compose = createComposeRule()

    private val ready = RivalryUiState(loading = false, myName = "Kevin", rivalName = "Julian")

    private fun show(
        state: RivalryUiState,
        onStart: (MatchSettings, String?) -> Unit = { _, _ -> },
        onResume: (String) -> Unit = {},
    ) = compose.setContent {
        RivalsTheme { RivalryContent(state, onStart, onResume, {}, {}, {}, {}, {}) }
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
        compose.onNodeWithText("9-ball").performClick()
        compose.onNodeWithText("+").performClick()
        compose.onNodeWithText("The Crown").performClick()
        compose.onNodeWithText("Start").performClick()

        assertEquals(MatchSettings(GameType.NINE_BALL, raceTo = 6) to "The Crown", started)
    }

    @Test
    fun anActiveSessionIsResumedNotRestarted() {
        var resumed: String? = null
        show(ready.copy(activeSession = ActiveSessionSummary("s1", 2, 1)), onResume = { resumed = it })

        compose.onNodeWithText("Start session").assertDoesNotExist()
        compose.onNodeWithText("Tonight 2 – 1").assertIsDisplayed()
        compose.onNodeWithText("Resume session").performClick()
        assertEquals("s1", resumed)
    }

    @Test
    fun theHeadToHeadIsShown() {
        show(ready.copy(myWins = 12, rivalWins = 9))
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("9").assertIsDisplayed()
        compose.onNodeWithText("You").assertIsDisplayed()
    }
}
