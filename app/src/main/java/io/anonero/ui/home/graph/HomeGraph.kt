/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.anonero.ui.home.graph

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import io.anonero.ui.home.HomeScreenComposable
import io.anonero.ui.home.LockScreen
import io.anonero.ui.home.graph.routes.Home
import io.anonero.ui.home.graph.routes.HomeScreenRoute
import io.anonero.ui.home.graph.routes.LockScreenRoute

/**
 * Top-level destination chooser for an unlocked process.
 *
 * A wallet whose status is already OK starts at the home content; otherwise it starts at
 * the lock screen. The lock route is part of the graph so a process that was launched
 * without a wallet can still reach onboarding from the landing graph.
 */
fun NavGraphBuilder.homeGraph(
    navController: NavHostController,
    startAtHome: Boolean,
) {
    navigation<Home>(
        startDestination = if (startAtHome) HomeScreenRoute() else LockScreenRoute,
    ) {
        composable<HomeScreenRoute> {
            HomeScreenComposable(mainNavController = navController)
        }
        composable<LockScreenRoute> {
            val walletState = org.koin.compose.koinInject<io.anonero.services.WalletState>()
            LockScreen(
                onUnLocked = { _, shortCut ->
                    navController.navigate(HomeScreenRoute()) {
                        popUpTo(LockScreenRoute) {
                            inclusive = true
                        }
                    }
                    if (shortCut != io.anonero.ui.home.LockScreenShortCut.HOME) {
                        walletState.emitUnlockShortcut(shortCut)
                    }
                },
            )
        }
    }
}
