package com.kevinbevan.rivals.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kevinbevan.rivals.ui.PlaceholderScreen
import com.kevinbevan.rivals.ui.home.HomeScreen
import com.kevinbevan.rivals.ui.signin.SignInScreen

@Composable
fun RivalsNavHost() {
    val navController = rememberNavController()

    // TODO(milestone 2): pick the start destination from the auth state.
    NavHost(navController = navController, startDestination = SignInRoute) {
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
                onStartSession = { navController.navigate(SessionRoute(sessionId = "placeholder")) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenStats = { navController.navigate(StatsRoute) },
            )
        }
        composable<SessionRoute> { entry ->
            PlaceholderScreen("Session ${entry.toRoute<SessionRoute>().sessionId}") { navController.popBackStack() }
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
