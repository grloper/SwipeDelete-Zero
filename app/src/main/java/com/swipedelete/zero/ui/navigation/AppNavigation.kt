package com.swipedelete.zero.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.ui.screens.cloud.CloudManagerScreen
import com.swipedelete.zero.ui.screens.dashboard.DashboardScreen
import com.swipedelete.zero.ui.screens.dual.DualCardSplitScreen
import com.swipedelete.zero.ui.screens.settings.SettingsScreen
import com.swipedelete.zero.ui.screens.setup.CloudSetupScreen
import com.swipedelete.zero.ui.screens.staging.StagingDrawerScreen
import com.swipedelete.zero.ui.screens.swipe.SwipeEngineScreen

/**
 * Single-activity Compose nav graph. Comparison decks (duplicates/blurry) route
 * to the dual-card split screen; everything else to the single-card engine.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    fun backFrom(route: String) {
        if (navController.currentDestination?.route == route) navController.popBackStack()
    }
    NavHost(
        navController = navController, startDestination = Routes.DASHBOARD,
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 24 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 24 } },
    ) {
        composable(Routes.DASHBOARD) {
            // Staging now presents as a modal bottom sheet hosted by the
            // dashboard itself; Routes.STAGING stays as a full-screen fallback.
            DashboardScreen(
                onOpenDeck = { deck: Deck ->
                    if (navController.currentDestination?.route != Routes.DASHBOARD) return@DashboardScreen
                    if (deck.kind.isComparison) {
                        navController.navigate(Routes.dualCard(deck.id))
                    } else {
                        navController.navigate(Routes.swipeEngine(deck.id))
                    }
                },
                onOpenSettings = { if (navController.currentDestination?.route == Routes.DASHBOARD) navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.SWIPE_ENGINE,
            arguments = listOf(navArgument(Routes.ARG_DECK_ID) { type = NavType.StringType }),
        ) {
            SwipeEngineScreen(
                onBack = { backFrom(Routes.SWIPE_ENGINE) },
                onOpenCloudManager = { navController.navigate(Routes.CLOUD_MANAGER) },
            )
        }

        composable(
            route = Routes.DUAL_CARD,
            arguments = listOf(navArgument(Routes.ARG_DECK_ID) { type = NavType.StringType }),
        ) {
            DualCardSplitScreen(onBack = { backFrom(Routes.DUAL_CARD) })
        }

        composable(Routes.STAGING) {
            StagingDrawerScreen(
                onBack = { backFrom(Routes.STAGING) },
                onOpenBackupSetup = { navController.navigate(Routes.CLOUD_SETUP) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { backFrom(Routes.SETTINGS) },
                onOpenCloudSetup = { navController.navigate(Routes.CLOUD_SETUP) },
                onOpenCloudManager = { navController.navigate(Routes.CLOUD_MANAGER) },
            )
        }

        composable(Routes.CLOUD_SETUP) {
            CloudSetupScreen(onBack = { backFrom(Routes.CLOUD_SETUP) })
        }

        composable(Routes.CLOUD_MANAGER) {
            CloudManagerScreen(onBack = { backFrom(Routes.CLOUD_MANAGER) })
        }
    }
}
