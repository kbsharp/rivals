package com.kevinbevan.rivals.ui.session

import com.kevinbevan.rivals.data.GuestRepository
import com.kevinbevan.rivals.data.LocalSessionStore
import com.kevinbevan.rivals.data.SessionRepository
import com.kevinbevan.rivals.domain.ScoreRules
import com.kevinbevan.rivals.model.FrameEvent
import com.kevinbevan.rivals.model.GameType
import com.kevinbevan.rivals.model.MatchSettings
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scoreboard's last-frame receipt and its notices, played on the phone's own store. The
 * "other phone" is the repository used directly, recording as the rival.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private var nextId = 0
    private val rules = ScoreRules(newId = { "id${nextId++}" })
    private var clock = Instant.parse("2026-09-26T19:30:00Z")
    private val a = GuestRepository.PLAYER_A
    private val b = GuestRepository.PLAYER_B

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun reset() = Dispatchers.resetMain()

    private class Board(val vm: SessionViewModel, val other: SessionRepository, val id: String) {
        val state get() = vm.uiState.value
    }

    /** A night between Kevin (this phone, [a]) and Julian (the other phone, [b]). */
    private fun TestScope.board(guest: Boolean = false): Board {
        val store = LocalSessionStore(null, now = { clock }, scope = this)
        val sessions = SessionRepository(store, rules)
        val id = GuestRepository(store, rules).startGame("Kevin" to "Julian", MatchSettings(GameType.NINE_BALL, raceTo = 5))
        val vm = SessionViewModel(id, guest = guest, currentUid = { a }, sessionRepository = sessions)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        return Board(vm, sessions, id)
    }

    @Test
    fun theReceiptNamesTheLastFrameAndWhichPhoneRecordedIt() = runTest {
        val board = board()
        assertNull(board.state.lastFrame)
        board.vm.recordFrame(a)
        advanceUntilIdle()
        assertEquals(FrameReceipt(1, a, "Kevin", theirPhone = false, events = emptySet()), board.state.lastFrame)

        clock = clock.plusSeconds(120)
        board.other.recordFrame(board.id, b, recordedBy = b)
        board.other.toggleLastFrameEvent(board.id, FrameEvent.BREAK_AND_RUN)
        advanceUntilIdle()
        val receipt = board.state.lastFrame!!
        assertEquals(2, receipt.number)
        assertEquals("Julian", receipt.winnerName)
        assertTrue(receipt.theirPhone)
        assertEquals(setOf(FrameEvent.BREAK_AND_RUN), receipt.events)
        assertNull(receipt.double)
    }

    @Test
    fun twoFramesFromBothPhonesWithinTenSecondsAreFlaggedAsADouble() = runTest {
        val board = board()
        board.vm.recordFrame(b)
        clock = clock.plusSeconds(4)
        board.other.recordFrame(board.id, b, recordedBy = b)
        advanceUntilIdle()
        assertEquals(DoubleFrame(first = 1, secondsApart = 4), board.state.lastFrame!!.double)

        // Undo clears it: the frame left is on its own.
        board.vm.undo()
        advanceUntilIdle()
        assertNull(board.state.lastFrame!!.double)
    }

    @Test
    fun anUndoIsNamedOnBothPhones() = runTest {
        val board = board()
        board.vm.recordFrame(a)
        board.vm.recordFrame(a)
        board.vm.recordFrame(b)
        advanceUntilIdle()

        board.vm.undo()
        advanceUntilIdle()
        assertEquals("Kevin undid frame 3 · 2 – 1 → 2 – 0", board.state.notice?.text)
        board.vm.dismissNotice(board.state.notice!!)
        assertNull(board.state.notice)

        // From the other phone: this one didn't ask, so it names the rival.
        board.other.undoLastFrame(board.id)
        advanceUntilIdle()
        val notice = board.state.notice
        assertNotNull(notice)
        assertEquals("Julian undid frame 2 · 2 – 0 → 1 – 0", notice!!.text)
        assertFalse(notice.warning)
    }

    @Test
    fun aGuestGameDoesntSayWhoUndid() = runTest {
        val board = board(guest = true)
        board.vm.recordFrame(b)
        advanceUntilIdle()
        assertFalse(board.state.lastFrame!!.theirPhone)
        board.vm.undo()
        advanceUntilIdle()
        assertEquals("Frame 1 undone · 0 – 1 → 0 – 0", board.state.notice?.text)
    }

    @Test
    fun theRaceCanBeChangedMidMatchFromTheSheet() = runTest {
        val board = board()
        repeat(3) { board.vm.recordFrame(a) }
        advanceUntilIdle()
        board.vm.changeSettings(MatchSettings(GameType.TEN_BALL, raceTo = 7))
        advanceUntilIdle()
        assertEquals(MatchSettings(GameType.TEN_BALL, raceTo = 7), board.state.match!!.settings)
        // Below the leader is refused, and said so on the status line.
        board.vm.changeSettings(MatchSettings(GameType.TEN_BALL, raceTo = 3))
        advanceUntilIdle()
        assertEquals(7, board.state.match!!.settings.raceTo)
        assertTrue(board.state.notice!!.warning)
    }
}
