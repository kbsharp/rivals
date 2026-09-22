package com.kevinbevan.rivals.ui.signin

import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.kevinbevan.rivals.data.isPermissionDenied

/** What to tell the user when sign-in fails with [e]. */
fun signInErrorMessage(e: Throwable): String = when {
    e is NoCredentialException ->
        "No Google account on this device. Add one in Settings, then try again."
    // No credential provider: Google Play services is missing or too old for Credential Manager.
    e is GetCredentialProviderConfigurationException || e is GetCredentialUnsupportedException ->
        "Google sign-in isn't available on this phone. Update Google Play services from the Play Store, then try again."
    e.isPermissionDenied() -> "Signed in, but your profile couldn't be saved. Try again."
    else -> "Sign-in failed: ${e.message ?: e::class.simpleName}"
}
