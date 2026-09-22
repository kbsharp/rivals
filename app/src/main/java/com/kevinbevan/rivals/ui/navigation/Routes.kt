package com.kevinbevan.rivals.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object SignInRoute
@Serializable data class RivalryRoute(val rivalryId: String)
@Serializable object AddRivalRoute
@Serializable data class InviteRoute(val code: String)

/** [guest]: a quick game kept on the phone rather than a rivals' session in Firestore. */
@Serializable data class SessionRoute(val sessionId: String, val guest: Boolean = false)
@Serializable data class SessionDetailRoute(val sessionId: String, val guest: Boolean = false)

/** Share links open [InviteRoute]: `https://rivals-15bd9.web.app/invite/<code>`. */
object InviteLinks {
    const val BASE = "https://rivals-15bd9.web.app/invite"

    fun forCode(code: String) = "$BASE/$code"
}
