package com.kevinbevan.rivals.data

import com.kevinbevan.rivals.model.Session
import com.kevinbevan.rivals.model.Status
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionOrderingTest {
    private fun s(id: String, status: Status, startedAt: Long?) = Session(
        id = id,
        playerIds = listOf("a", "b"),
        status = status,
        startedAt = startedAt?.let(Instant::ofEpochSecond),
        createdBy = "a",
        matchWins = emptyMap(),
    )

    @Test
    fun pastSessionsAreEndedOnlyNewestFirst() {
        val sessions = listOf(
            s("old", Status.ENDED, 100),
            s("live", Status.ACTIVE, 400),
            s("new", Status.ENDED, 300),
            s("undated", Status.ENDED, null),
            s("mid", Status.ENDED, 200),
        )
        assertEquals(
            listOf("new", "mid", "old", "undated"),
            SessionRepository.pastSessions(sessions).map { it.id },
        )
    }

    @Test
    fun oldestActiveWinsWhenTwoWereStarted() {
        val sessions = listOf(
            s("later", Status.ACTIVE, 200),
            s("ended", Status.ENDED, 50),
            s("earlier", Status.ACTIVE, 100),
        )
        assertEquals("earlier", SessionRepository.oldestActive(sessions)?.id)
        assertNull(SessionRepository.oldestActive(listOf(s("ended", Status.ENDED, 50))))
    }
}
