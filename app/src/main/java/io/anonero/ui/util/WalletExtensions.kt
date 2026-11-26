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

package io.anonero.ui.util

import io.anonero.model.Subaddress
import io.anonero.model.Wallet

/** Append exactly one index and expose it only after wallet2 confirms the count and store. */
internal fun appendStoredSubaddress(
    highestObserved: Int,
    count: () -> Int,
    append: (Int) -> Unit,
    store: () -> Boolean,
): Int? {
    val next = count()
    if (next <= 0 || next == Int.MAX_VALUE || highestObserved >= next) return null
    append(next)
    return next.takeIf { count() == next + 1 && store() }
}

internal fun Wallet.issueSubaddress(account: Int): Subaddress? {
    val transactions = history?.takeIf { it.accountIndex == account }?.all ?: return null
    val highestTransaction = transactions.asSequence().filter { it.accountIndex == account }.maxOfOrNull { it.addressIndex } ?: 0
    val highestOutput =
        coins
            ?.all
            ?.asSequence()
            ?.filter { it.subaddrAccount == account }
            ?.maxOfOrNull { it.subaddrIndex } ?: 0
    val next =
        appendStoredSubaddress(
            highestObserved = maxOf(highestTransaction, highestOutput),
            count = { getNumSubaddressesFor(account) },
            append = { index -> addSubaddress(account, "Subaddress #$index") },
            store = { store() },
        ) ?: return null
    return getSubaddressObject(account, next).takeIf { validateAddress(it.address) }
}

fun Wallet.getAllUsedSubAddresses(): List<Subaddress> {
    val account = getAccountIndex()
    val subAddresses = arrayListOf<Subaddress>()
    for (i in 0 until getNumSubaddressesFor(account)) {
        subAddresses.add(getSubaddressObject(account, i))
    }
    return subAddresses
        .apply {
            this.removeIf { it.addressIndex == 0 && it.totalAmount != 0L }
        }.distinctBy { it.address }
        .sortedBy { it.addressIndex }
}
