package com.kevinbevan.rivals.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.kevinbevan.rivals.RivalsApp
import com.kevinbevan.rivals.ui.history.HistoryScreen
import com.kevinbevan.rivals.ui.history.SessionDetailScreen
import com.kevinbevan.rivals.ui.home.HomeScreen
import com.kevinbevan.rivals.ui.invite.AddRivalScreen
import com.kevinbevan.rivals.ui.invite.InviteScreen
import com.kevinbevan.rivals.ui.rivalry.RivalryScreen
import com.kevinbevan.rivals.ui.session.SessionScreen
import com.kevinbevan.rivals.ui.signin.SignInScreen
import com.kevinbevan.rivals.ui.stats.StatsScreen

@Composable
fun RivalsNavHost() {
    val navController = rememberNavController()
    val authRepository = (LocalContext.current.applicationContext as RivalsApp).container.authRepository
    val user by authRepository.authState.collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

    // Signing out leaves the rivals' screens: back to Home, which works without an account.
    // A guest game carries on.
    LaunchedEffect(user) {
        val dest = navController.currentDestination ?: return@LaunchedEffect
        val guestScreen = dest.hasRoute(HomeRoute::class) || dest.hasRoute(SignInRoute::class) ||
            dest.hasRoute(InviteRoute::class) ||
            (dest.hasRoute(SessionRoute::class) || dest.hasRoute(SessionDetailRoute::class)) &&
            navController.currentBackStackEntry?.arguments?.getBoolean("guest") == true
        if (user == null && !guestScreen) navController.popBackStack(HomeRoute, inclusive = false)
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onSignIn = { navController.navigate(SignInRoute) },
                onOpenRivalry = { navController.navigate(RivalryRoute(it)) },
                onAddRival = { navController.navigate(AddRivalRoute) },
                onOpenSession = { navController.navigate(it) },
                onOpenGuestGame = { navController.navigate(SessionDetailRoute(it, guest = true)) },
            )
        }
        composable<SignInRoute> {
            SignInScreen(onSignedIn = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable<RivalryRoute> {
            RivalryScreen(
                onOpenSession = { navController.navigate(SessionRoute(it)) },
                onOpenHistory = { navController.navigate(HistoryRoute(it)) },
                onOpenStats = { navController.navigate(StatsRoute(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<AddRivalRoute> {
            AddRivalScreen(
                onOpenInvite = { navController.navigate(InviteRoute(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<InviteRoute>(deepLinks = listOf(navDeepLink<InviteRoute>(basePath = InviteLinks.BASE))) {
            InviteScreen(
                onSignIn = { navController.navigate(SignInRoute) },
                onAccepted = { rivalryId ->
                    navController.navigate(RivalryRoute(rivalryId)) { popUpTo(HomeRoute) }
                },
                onBack = { if (!navController.popBackStack()) navController.navigate(HomeRoute) },
            )
        }
        composable<SessionRoute> {
            SessionScreen(onExit = { navController.popBackStack() })
        }
        composable<HistoryRoute> {
            HistoryScreen(
                onOpenSession = { navController.navigate(SessionDetailRoute(sessionId = it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SessionDetailRoute> {
            SessionDetailScreen(onBack = { navController.popBackStack() })
        }
        composable<StatsRoute> {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
