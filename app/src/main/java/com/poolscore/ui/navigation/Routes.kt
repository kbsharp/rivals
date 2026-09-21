package com.poolscore.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object SignInRoute
@Serializable object HomeRoute
@Serializable data class SessionRoute(val sessionId: String)
@Serializable object HistoryRoute
@Serializable data class SessionDetailRoute(val sessionId: String)
@Serializable object StatsRoute
