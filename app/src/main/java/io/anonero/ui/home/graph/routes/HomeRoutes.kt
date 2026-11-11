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

package io.anonero.ui.home.graph.routes

import io.anonero.ui.home.LockScreenShortCut
import kotlinx.serialization.Serializable

@Serializable
data class HomeScreenRoute(
    val lockScreenShortCut: LockScreenShortCut? = null,
)

@Serializable
data object LockScreenRoute

@Serializable
data object Home

@Serializable
data object TransactionsRoute

@Serializable
data class TransactionDetailRoute(
    val transactionId: String,
)

@Serializable
data object ReceiveRoute

@Serializable
data class ReviewTransactionRoute(
    val toAddress: String,
    val addresses: List<String> = emptyList(),
    val amounts: List<Long> = emptyList(),
)

@Serializable
data object AccountsRoute

@Serializable
data object PayToManyRoute

@Serializable
data object SignVerifyRoute

@Serializable
data object DecoySettingsRoute

@Serializable
data object LwsSettingsRoute

@Serializable
data object SettingsRoute

@Serializable
data object SettingsViewSeedRoute

@Serializable
data object SettingsExportBackUp

@Serializable
data object SettingsLogs

@Serializable
data object CoinsScreenRoute

@Serializable
data object SettingsNodeRoute

@Serializable
data object ProxySettingsRoute

@Serializable
data object SecureWipeRoute

@Serializable
data object SubAddressesRoute

@Serializable
data class SubAddressDetailRoute(
    val subAddress: String,
)
