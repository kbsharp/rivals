package com.kevinbevan.rivals.model

import com.kevinbevan.rivals.domain.ScoreRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayNamesTest {
    private fun p(uid: String, name: String, email: String) = Player(uid, name, email, null)

    @Test
    fun differentFirstNamesAreEnough() {
        val names = displayNames(listOf(p("a", "Kevin Bevan", "k@x.com"), p("b", "Julian Jones", "j@x.com")))
        assertEquals(mapOf("a" to "Kevin", "b" to "Julian"), names)
    }

    @Test
    fun sharedFirstNamesFallBackToFullNames() {
        val names = displayNames(listOf(p("a", "Kevin Bevan", "k@x.com"), p("b", "kevin Smith", "s@x.com")))
        assertEquals(mapOf("a" to "Kevin Bevan", "b" to "Kevin Smith"), names)
    }

    @Test
    fun identicalNamesFallBackToEmails() {
        val names = displayNames(listOf(p("a", "Kevin", "iambevan@gmail.com"), p("b", "kevin", "kbevan.dev@gmail.com")))
        assertEquals(mapOf("a" to "iambevan", "b" to "kbevan.dev"), names)
    }

    @Test
    fun namesAreCapitalisedButOtherwiseAsTyped() {
        assertEquals(mapOf("a" to "Julian", "b" to "Kevin"), displayNames(listOf(p("a", "julian", "j@x.com"), p("b", "Kevin B", "k@x.com"))))
        assertEquals("Mary-jane McDonald", capitalised(" mary-jane mcDonald "))
        assertEquals("", capitalised(""))
    }

    @Test
    fun breaksAlternate() {
        assertEquals("b", ScoreRules.alternateBreaker("a", listOf("a", "b")))
        assertEquals("a", ScoreRules.alternateBreaker("b", listOf("a", "b")))
        assertNull(ScoreRules.alternateBreaker(null, listOf("a", "b")))
    }
}
