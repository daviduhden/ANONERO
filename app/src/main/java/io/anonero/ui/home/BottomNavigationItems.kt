package io.anonero.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.anonero.AnonConfig
import io.anonero.R
import io.anonero.icons.AnonIcons
import io.anonero.ui.home.graph.routes.ReceiveRoute
import io.anonero.ui.home.graph.routes.SendScreenRoute
import io.anonero.ui.home.graph.routes.SettingsRoute
import io.anonero.ui.home.graph.routes.TransactionsRoute


data class BottomNavigationItem(
    val title: String,
    val icon: ImageVector,
    val route: Any
) {

    val getRouteAsString
        get(): String {
            return route::class.qualifiedName ?: ""
        }
}

fun getRoutes(): ArrayList<BottomNavigationItem> {
    return arrayListOf(
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.transactions) ?: "",
            icon = AnonIcons.Home,
            route = TransactionsRoute
        ),
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.receive) ?: "",
            icon = AnonIcons.QrCode,
            route = ReceiveRoute
        ),
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.send) ?: "",
            icon = Icons.AutoMirrored.Outlined.Send,
            route = SendScreenRoute("")
        ),
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.settings) ?: "",
            icon = Icons.Default.Settings,
            route = SettingsRoute
        ),

        )
}