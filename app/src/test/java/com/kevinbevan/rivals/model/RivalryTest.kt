package com.kevinbevan.rivals.model

import com.kevinbevan.rivals.data.PlayerRepository
import com.kevinbevan.rivals.data.RivalryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RivalryTest {
    @Test
    fun aPairHasOneIdWhoeverAsks() {
        assertEquals("abc_xyz", Rivalry.idFor("xyz", "abc"))
        assertEquals(Rivalry.idFor("abc", "xyz"), Rivalry.idFor("xyz", "abc"))
        assertEquals(listOf("abc", "xyz"), Rivalry.playersOf("abc_xyz"))
    }

    @Test
    fun theRivalIsTheOtherPlayer() {
        val r = Rivalry("a_b", listOf("a", "b"), RivalryStatus.ACTIVE, invitedBy = "a")
        assertEquals("b", r.rivalOf("a"))
        assertEquals("a", r.rivalOf("b"))
    }

    @Test
    fun inviteCodesAreReadableAndForgiving() {
        repeat(50) {
            val code = RivalryRepository.newInviteCode()
            assertEquals(8, code.length)
            assertTrue(code, code.none { it in "01OIL" })
            assertEquals(code, RivalryRepository.normaliseInviteCode(code.lowercase().chunked(4).joinToString("-")))
        }
        // Characters never used in a code can't be one.
        assertEquals("", RivalryRepository.normaliseInviteCode("ABCD-0000"))
    }

    @Test
    fun emailsAreMatchedIgnoringCaseAndSpaces() {
        assertEquals("julian@gmail.com", PlayerRepository.normaliseEmail("  Julian@Gmail.com "))
    }
}
