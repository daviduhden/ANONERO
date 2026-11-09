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

package io.anonero.model

/**
 * A Monero subaddress account inside one wallet file (not a separate wallet).
 *
 * Balances are atomic units. [index] is the wallet2 major account index used by
 * `Wallet.setAccountIndex`.
 */
data class AccountInfo(
    val index: Int,
    val label: String,
    val address: String,
    val balance: Long,
    val unlockedBalance: Long,
)
