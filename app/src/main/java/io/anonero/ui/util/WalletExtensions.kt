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

fun Wallet.getLastUnusedIndex(): Int {
    // The number of created subaddresses, not only received payments, determines the
    // next index. Otherwise creating several addresses without receiving funds reuses
    // index 1 and links them.
    var lastIndex = this.numSubAddresses - 1
    for (info in this.history?.all ?: listOf()) {
        if (info.addressIndex > lastIndex) lastIndex = info.addressIndex
    }
    return lastIndex
}

internal fun nextSubaddressIndex(
    lastUsed: Int,
    count: Int,
): Int = maxOf(lastUsed, count - 1) + 1

fun Wallet.getLatestSubAddress(): Subaddress {
    val nextIndex = nextSubaddressIndex(getLastUnusedIndex(), this.numSubAddresses)
    // Create the next address if the wallet does not have it yet.
    if (nextIndex >= this.numSubAddresses) {
        this.addSubaddress(getAccountIndex(), "Subaddress #$nextIndex")
        this.store()
    }
    return this.getSubaddressObject(nextIndex)
}

fun Wallet.getAllUsedSubAddresses(): List<Subaddress> {
    val subAddresses = arrayListOf<Subaddress>()
    for (i in 0 until this.numSubAddresses) {
        subAddresses.add(this.getSubaddressObject(i))
    }
    // Do not call getLatestSubAddress() here: getters must not create or store addresses.
    return subAddresses
        .apply {
            this.removeIf { it.addressIndex == 0 && it.totalAmount != 0L }
        }.distinctBy { it.address }
        .sortedBy { it.addressIndex }
}
