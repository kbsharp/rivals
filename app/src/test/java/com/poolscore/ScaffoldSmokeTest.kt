package com.poolscore

import com.poolscore.ui.navigation.SessionRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaffoldSmokeTest {
    @Test
    fun sessionRouteCarriesId() {
        assertEquals("abc", SessionRoute(sessionId = "abc").sessionId)
    }
}
