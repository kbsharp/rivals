package com.kevinbevan.rivals.ui

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.kevinbevan.rivals.data.isPermissionDenied

/** Turns a failure into something to show. */
fun messageFor(e: Throwable): String = when {
    e.isPermissionDenied() -> "You don't have access to that. It may have been removed."
    e is FirebaseNetworkException ||
        (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE) ->
        "No connection. Try again when you have signal."
    else -> e.message ?: e::class.simpleName ?: "Something went wrong"
}
