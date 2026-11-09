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

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeLong(amount)
        out.writeString(address)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Creator<Transfer> {
        override fun createFromParcel(parcel: Parcel): Transfer {
            return Transfer(parcel)
        }

        override fun newArray(size: Int): Array<Transfer?> {
            return arrayOfNulls(size)
        }
    }
}