package com.kevinbevan.rivals.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kevinbevan.rivals.data.isPermissionDenied
import com.kevinbevan.rivals.domain.Schema
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** `firestore.rules` against the emulator: only the two verified allow-listed accounts get in. */
@RunWith(AndroidJUnit4::class)
class RulesTest {

    @Before
    fun setUp() = Emulators.reset()

    @Test
    fun anAllowedAccountCanReadAndWrite(): Unit = runBlocking {
        TestClient(TestClient.PLAYER_A).use { client ->
            client.signIn()
            client.db.collection(Schema.SESSIONS).document("probe").set(mapOf("x" to 1)).await()
            client.db.collection(Schema.SESSIONS).get().await()
        }
    }

    @Test
    fun anotherGoogleAccountIsRefused(): Unit = runBlocking {
        TestClient("stranger@example.com").use { client ->
            client.signIn()
            assertDenied { client.db.collection(Schema.SESSIONS).get().await() }
            assertDenied { client.db.collection(Schema.SESSIONS).document("x").set(mapOf("x" to 1)).await() }
        }
    }

    @Test
    fun anUnverifiedEmailIsRefused(): Unit = runBlocking {
        TestClient(TestClient.PLAYER_A, emailVerified = false).use { client ->
            client.signIn()
            assertDenied { client.db.collection(Schema.SESSIONS).get().await() }
        }
    }

    @Test
    fun signedOutIsRefused(): Unit = runBlocking {
        TestClient(TestClient.PLAYER_A).use { client ->
            assertDenied { client.db.collection(Schema.SESSIONS).get().await() }
        }
    }

    private suspend fun assertDenied(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            assertTrue("Expected PERMISSION_DENIED, got $e", e.isPermissionDenied())
            return
        }
        fail("Expected PERMISSION_DENIED, but it was allowed")
    }
}
