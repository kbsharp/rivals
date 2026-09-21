package com.kevinbevan.rivals.data

import com.google.firebase.firestore.FirebaseFirestoreException

/** True when Firestore rejected the request because the account isn't on the allow-list. */
fun Throwable.isPermissionDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED
