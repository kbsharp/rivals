package com.kevinbevan.rivals.data

import com.google.firebase.firestore.FirebaseFirestoreException

/** True when Firestore rejected the request because the account isn't on the allow-list. */
fun Throwable.isPermissionDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED

const val NOT_ALLOWED_MESSAGE = "This account isn't allowed. Sign in with one of the two Rivals accounts."
