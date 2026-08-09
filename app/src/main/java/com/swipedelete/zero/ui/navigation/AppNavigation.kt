package com.swipedelete.zero.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.ui.screens.dashboard.DashboardScreen
import com.swipedelete.zero.ui.screens.dual.DualCardSplitScreen
import com.swipedelete.zero.ui.screens.settings.SettingsScreen
import com.swipedelete.zero.ui.screens.staging.StagingDrawerScreen
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineScreen

/**
 * Single-activity Compose nav graph. Comparison decks (duplicates/blurry) route
 * to the dual-card split screen; everything else to the single-card engine.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            // Staging now presents as a modal bottom sheet hosted by the
            // dashboard itself; Routes.STAGING stays as a full-screen fallback.
            DashboardScreen(
                onOpenDeck = { deck: Deck ->
                    if (deck.kind.isComparison) {
                        navController.navigate(Routes.dualCard(deck.id))
                    } else {
                        navController.navigate(Routes.swipeEngine(deck.id))
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.SWIPE_ENGINE,
            arguments = listOf(navArgument(Routes.ARG_DECK_ID) { type = NavType.StringType }),
        ) {
            SwipeEngineScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.DUAL_CARD,
            arguments = listOf(navArgument(Routes.ARG_DECK_ID) { type = NavType.StringType }),
        ) {
            DualCardSplitScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.STAGING) {
            StagingDrawerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
