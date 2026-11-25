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
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionParcelTest {
    @Test
    fun nullableTransferListKeepsNotesAligned() {
        val knownTransfer = Transfer(42, "test destination")
        for (transfers in listOf(null, emptyList<Transfer>(), listOf(knownTransfer))) {
            val original = TransactionInfo(0, false, false, 42, 0, 1, null, 1, null, 2, 3, 1, null, transfers)
            original.notes = "test note"
            val parcel = Parcel.obtain()
            try {
                original.writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                val restored = TransactionInfo.CREATOR.createFromParcel(parcel)
                assertEquals(original.notes, restored.notes)
                assertEquals(original.accountIndex, restored.accountIndex)
                assertEquals(transfers?.map { it.amount to it.address }, restored.transfers?.map { it.amount to it.address })
                assertEquals(0, parcel.dataAvail())
            } finally {
                parcel.recycle()
            }
        }
    }
}
