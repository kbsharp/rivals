package com.kevinbevan.rivals.ui.signin

import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInErrorsTest {
    @Test
    fun noAccountOnThePhone() {
        assertTrue(signInErrorMessage(NoCredentialException()).startsWith("No Google account"))
    }

    @Test
    fun outdatedPlayServicesSaysHowToFixIt() {
        // What an Android 8 emulator with old Play services threw, and used to crash on.
        for (e in listOf(GetCredentialProviderConfigurationException(), GetCredentialUnsupportedException())) {
            assertTrue(signInErrorMessage(e).contains("Update Google Play services"))
        }
    }

    @Test
    fun anythingElseShowsItsMessage() {
        assertEquals("Sign-in failed: boom", signInErrorMessage(RuntimeException("boom")))
    }
}
