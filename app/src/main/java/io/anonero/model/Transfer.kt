/*
 * Copyright (c) 2026, David Uhden Collado
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

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import kotlinx.serialization.Serializable

/**
 * One destination of a wallet2 transaction.
 *
 * This is the per-output amount/address pair exposed by `TransactionInfo.transfers`.
 * [amount] is in atomic units. The list can be absent for transactions whose transfer
 * details wallet2 does not report; callers must handle a null list.
 */
@Serializable
class Transfer : Parcelable {
    var amount: Long
    var address: String?

    constructor(amount: Long, address: String?) {
        this.amount = amount
        this.address = address
    }

    private constructor(`in`: Parcel) {
        amount = `in`.readLong()
        address = `in`.readString()
    }

    override fun writeToParcel(
        out: Parcel,
        flags: Int,
    ) {
        out.writeLong(amount)
        out.writeString(address)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Creator<Transfer> {
        override fun createFromParcel(parcel: Parcel): Transfer = Transfer(parcel)

        override fun newArray(size: Int): Array<Transfer?> = arrayOfNulls(size)
    }
}
