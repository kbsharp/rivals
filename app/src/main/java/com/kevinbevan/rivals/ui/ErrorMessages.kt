package com.kevinbevan.rivals.ui

import com.kevinbevan.rivals.auth.AuthRepository
import com.kevinbevan.rivals.data.NOT_ALLOWED_MESSAGE
import com.kevinbevan.rivals.data.isPermissionDenied

/**
 * Turns a Firestore failure into something to show. PERMISSION_DENIED means this account is
 * off the allow-list and nothing will work, so it also signs out (once: after sign-out every
 * listener fails the same way, but there's no user left to sign out).
 */
suspend fun AuthRepository.messageFor(e: Throwable): String {
    if (!e.isPermissionDenied()) return e.message ?: e::class.simpleName ?: "Something went wrong"
    if (currentUser != null) signOut(reason = NOT_ALLOWED_MESSAGE)
    return NOT_ALLOWED_MESSAGE
}
