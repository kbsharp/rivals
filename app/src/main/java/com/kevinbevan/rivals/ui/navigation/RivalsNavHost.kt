package com.kevinbevan.rivals.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.kevinbevan.rivals.RivalsApp
import com.kevinbevan.rivals.ui.PlaceholderScreen
import com.kevinbevan.rivals.ui.home.HomeScreen
import com.kevinbevan.rivals.ui.session.SessionScreen
import com.kevinbevan.rivals.ui.signin.SignInScreen

@Composable
fun RivalsNavHost() {
    val navController = rememberNavController()
    val authRepository = (LocalContext.current.applicationContext as RivalsApp).container.authRepository
    val startDestination: Any = remember { if (authRepository.currentUser != null) HomeRoute else SignInRoute }
    val user by authRepository.authState.collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

    // Signing out (from anywhere) drops back to sign-in with a cleared back stack.
    // Signing in navigates from SignInScreen instead, once the allow-list check has passed.
    LaunchedEffect(user) {
        if (user == null && navController.currentDestination?.hasRoute(SignInRoute::class) == false) {
            navController.navigate(SignInRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<SignInRoute> {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(HomeRoute) {
                        popUpTo(SignInRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenSession = { navController.navigate(SessionRoute(sessionId = it)) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenStats = { navController.navigate(StatsRoute) },
            )
        }
        composable<SessionRoute> {
            SessionScreen(onExit = { navController.popBackStack(HomeRoute, inclusive = false) })
        }
        composable<HistoryRoute> {
            PlaceholderScreen("History") { navController.popBackStack() }
        }
        composable<SessionDetailRoute> { entry ->
            PlaceholderScreen("Session detail ${entry.toRoute<SessionDetailRoute>().sessionId}") { navController.popBackStack() }
        }
        composable<StatsRoute> {
            PlaceholderScreen("Stats") { navController.popBackStack() }
        }
    }
}
