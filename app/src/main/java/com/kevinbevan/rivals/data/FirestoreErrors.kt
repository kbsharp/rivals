package com.kevinbevan.rivals.data

import com.google.firebase.firestore.FirebaseFirestoreException

/** True when Firestore rejected the request under the security rules. */
fun Throwable.isPermissionDenied(): Boolean =
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED
