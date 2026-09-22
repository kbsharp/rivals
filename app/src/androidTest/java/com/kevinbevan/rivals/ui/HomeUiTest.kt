package com.kevinbevan.rivals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import com.kevinbevan.rivals.ui.home.GuestGame
import com.kevinbevan.rivals.ui.home.GuestSide
import com.kevinbevan.rivals.ui.home.HomeActions
import com.kevinbevan.rivals.ui.home.HomeContent
import com.kevinbevan.rivals.ui.home.HomeUiState
import com.kevinbevan.rivals.ui.home.InviteCard
import com.kevinbevan.rivals.ui.home.RivalCard
import com.kevinbevan.rivals.ui.theme.RivalsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeUiTest {
    @get:Rule val compose = createComposeRule()

    private val signedOut = HomeUiState(loading = false)
    private val signedIn = HomeUiState(
        loading = false,
        signedIn = true,
        myName = "Kevin Bevan",
        rivals = listOf(RivalCard("r1", "Julian", myWins = 12, rivalWins = 9)),
    )
    private val finished = GuestGame("g1", active = false, startedAt = null, GuestSide("guest-a", "Tom", 2), GuestSide("guest-b", "Kevin", 1))

    private fun show(state: HomeUiState, actions: HomeActions = HomeActions()) =
        compose.setContent { RivalsTheme { HomeContent(state, actions) } }

    @Test
    fun aQuickGameNeedsNoAccount() {
        var started: Pair<Pair<String, String>, MatchSettings>? = null
        show(signedOut, HomeActions(onStartQuickGame = { names, settings -> started = names to settings }))

        compose.onNodeWithText("Got a rival?").assertIsDisplayed()
        compose.onNodeWithText("Quick game").performClick()
        compose.onNodeWithText("Player 1").performTextInput("Tom")
        compose.onNodeWithText("Player 2").performTextInput("Sam")
        compose.onNodeWithText("9-BALL").performClick()
        compose.onNodeWithText("Start").performClick()

        assertEquals(("Tom" to "Sam") to MatchSettings(GameType.NINE_BALL, raceTo = 5), started)
    }

    @Test
    fun aRunningQuickGameIsResumed() {
        var resumed: String? = null
        val running = finished.copy(id = "g2", active = true)
        show(signedOut.copy(guestGames = listOf(running)), HomeActions(onResumeQuickGame = { resumed = it }))
        compose.onNodeWithText("Tom 2 – 1 Kevin").assertIsDisplayed()
        compose.onNodeWithText("Resume quick game").performClick()
        assertEquals("g2", resumed)
    }

    @Test
    fun rivalsAndInvitesAreListed() {
        var accepted: String? = null
        var opened: String? = null
        show(
            signedIn.copy(
                invites = listOf(InviteCard("r2", "Sam", incoming = true), InviteCard("r3", "Alex", incoming = false)),
            ),
            HomeActions(onAcceptInvite = { accepted = it }, onOpenRivalry = { opened = it }),
        )
        compose.onNodeWithText("Sam wants a rivalry").assertIsDisplayed()
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText("Waiting for them to accept").assertIsDisplayed()
        compose.onNodeWithText("Accept").performClick()
        // The one rival is Home's scoreboard, so the primary action opens their rivalry.
        compose.onNodeWithText("Play Julian").performClick()
        assertEquals("r2", accepted)
        assertEquals("r1", opened)
    }

    @Test
    fun aFinishedGuestGameIsSavedAsYouAgainstTheChosenRival() {
        var saved: Triple<String, String, String>? = null
        show(
            signedIn.copy(guestGames = listOf(finished)),
            HomeActions(onSaveGuestGame = { game, rivalry, me -> saved = Triple(game, rivalry, me) }),
        )
        compose.onNodeWithText("ON THIS PHONE").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        // The only rival is preselected; which side was you is picked here.
        compose.onNodeWithText("WHICH ONE WAS YOU?").assertIsDisplayed()
        compose.onNodeWithText("TOM").performClick()
        compose.onAllNodesWithText("Save").onLast().performClick() // the dialog's, not the row's
        assertEquals(Triple("g1", "r1", "guest-a"), saved)
    }

    @Test
    fun signedOutGuestGamesCantBeSavedYet() {
        show(signedOut.copy(guestGames = listOf(finished)))
        compose.onNodeWithText("Tom 2 – 1 Kevin").assertIsDisplayed()
        compose.onNodeWithText("Sign in and add a rival", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun namesCanBeChangedBeforeStarting() {
        var started: Pair<String, String>? = null
        show(signedIn, HomeActions(onStartQuickGame = { names, _ -> started = names }))
        compose.onNodeWithText("Quick game").performClick()
        compose.onNodeWithText("Kevin Bevan").performTextReplacement("Kev")
        compose.onNodeWithText("Start").performClick()
        assertEquals("Kev" to "", started)
    }
}
