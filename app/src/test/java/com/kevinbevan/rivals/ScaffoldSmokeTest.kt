package com.kevinbevan.rivals

import com.kevinbevan.rivals.ui.navigation.SessionRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldSmokeTest {
    @Test
    fun sessionRouteCarriesId() {
        assertEquals("abc", SessionRoute(sessionId = "abc").sessionId)
    }
}
