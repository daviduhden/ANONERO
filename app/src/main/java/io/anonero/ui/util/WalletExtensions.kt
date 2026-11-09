/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2023-2026 r4v3r23
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
    var lastUsedSubaddress = 0
    val subaddress = arrayListOf<Subaddress>()
    for (i in 0 until this.numSubAddresses) {
        subaddress.add(this.getSubaddressObject(i))
    }
    for (info in this.history?.all ?: listOf()) {
        if (info.addressIndex > lastUsedSubaddress) lastUsedSubaddress = info.addressIndex
    }
    return lastUsedSubaddress
}


fun Wallet.getLatestSubAddress(): Subaddress {
    val lastUsedSubAddress = getLastUnusedIndex()
    //get the next address
    val address = this.getSubaddressObject(lastUsedSubAddress + 1)
    //create the next address if the wallet does not have it yet
    if (address.addressIndex >= this.numSubAddresses) {
        this.addSubaddress(getAccountIndex(), "Subaddress #${address.addressIndex}")
        this.store()
    }
    return address
}


fun Wallet.getAllUsedSubAddresses(): List<Subaddress> {
    val subAddresses = arrayListOf<Subaddress>()
    for (i in 0 until this.numSubAddresses) {
        subAddresses.add(this.getSubaddressObject(i))
    }
    this.getLatestSubAddress().let {
        if (subAddresses.indexOf(it) == -1) subAddresses.add(it)
    }
    return subAddresses
        .apply {
            this.removeIf { it.addressIndex == 0 && it.totalAmount != 0L }
        }
        .distinctBy { it.address }.sortedBy { it.addressIndex }
}
