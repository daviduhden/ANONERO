package io.anonero.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController


@Composable
inline fun <reified T : ViewModel> NavBackStackEntry.sharedViewModel(navController: NavController): T {
    val route = this.destination.parent?.route ?: return viewModel<T>()
    val entry = remember(this) {
        navController.getBackStackEntry(route)
    }
    return viewModel(entry)
}