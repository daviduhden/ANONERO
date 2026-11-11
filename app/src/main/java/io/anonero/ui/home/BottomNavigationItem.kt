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
    val route: Any,
) {
    val getRouteAsString
        get(): String {
            return route::class.qualifiedName ?: ""
        }
}

fun getRoutes(): ArrayList<BottomNavigationItem> =
    arrayListOf(
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.transactions) ?: "",
            icon = AnonIcons.Home,
            route = TransactionsRoute,
        ),
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.receive) ?: "",
            icon = AnonIcons.QrCode,
            route = ReceiveRoute,
        ),
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.send) ?: "",
            icon = Icons.AutoMirrored.Outlined.Send,
            route = SendScreenRoute(""),
        ),
        BottomNavigationItem(
            title = AnonConfig.context?.getString(R.string.settings) ?: "",
            icon = Icons.Default.Settings,
            route = SettingsRoute,
        ),
    )
