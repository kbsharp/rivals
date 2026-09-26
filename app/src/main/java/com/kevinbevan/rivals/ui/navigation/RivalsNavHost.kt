package com.kevinbevan.rivals.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.kevinbevan.rivals.RivalsApp
import com.kevinbevan.rivals.ui.history.SessionDetailScreen
import com.kevinbevan.rivals.ui.home.HomeScreen
import com.kevinbevan.rivals.ui.invite.AddRivalScreen
import com.kevinbevan.rivals.ui.invite.InviteScreen
import com.kevinbevan.rivals.ui.rivalry.RivalryScreen
import com.kevinbevan.rivals.ui.session.SessionScreen
import com.kevinbevan.rivals.ui.signin.SignInScreen

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
        composable<HomeRoute> { entry ->
            // A quick game's detail asks Home to save it, since Home holds the Save dialog.
            val saveRequest by entry.savedStateHandle.getStateFlow<String?>(SAVE_GAME, null).collectAsStateWithLifecycle()
            HomeScreen(
                saveRequest = saveRequest,
                onSaveRequestHandled = { entry.savedStateHandle[SAVE_GAME] = null },
                onSignIn = { navController.navigate(SignInRoute) },
                onOpenRivalry = { navController.navigate(RivalryRoute(it)) },
                onAddRival = { navController.navigate(AddRivalRoute) },
                onOpenSession = { navController.navigate(it) },
                onOpenGuestGame = { id, canSave -> navController.navigate(SessionDetailRoute(id, guest = true, canSave = canSave)) },
            )
        }
        composable<SignInRoute> {
            SignInScreen(onSignedIn = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable<RivalryRoute> {
            RivalryScreen(
                onOpenSession = { navController.navigate(SessionRoute(it)) },
                onOpenSessionDetail = { navController.navigate(SessionDetailRoute(sessionId = it)) },
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
            val route = it.toRoute<SessionRoute>()
            SessionScreen(
                onHome = { navController.popBackStack(HomeRoute, inclusive = false) },
                onSeeNight = {
                    navController.navigate(SessionDetailRoute(route.sessionId, route.guest)) {
                        popUpTo(HomeRoute)
                    }
                },
            )
        }
        composable<SessionDetailRoute> {
            val route = it.toRoute<SessionDetailRoute>()
            SessionDetailScreen(
                onBack = { navController.popBackStack() },
                onSave = if (route.guest && route.canSave) {
                    {
                        navController.previousBackStackEntry?.savedStateHandle?.set(SAVE_GAME, route.sessionId)
                        navController.popBackStack()
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** The quick game Session detail asks Home to save. */
private const val SAVE_GAME = "saveGame"
