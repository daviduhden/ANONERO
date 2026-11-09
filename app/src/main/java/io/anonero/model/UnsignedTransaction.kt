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
 * A wallet2 unsigned transaction set loaded from disk or imported over QR/UR.
 *
 * The set contains the real outputs `sign()` will construct. [confirmationMessage] is
 * wallet2's own summary of those outputs, and [SigningSummary] parses it for review.
 * The scalar `address`, `amount`, and `fee` getters are advisory metadata; do not build a
 * review solely from them.
 *
 * [sign] signs this loaded object. Do not reload the file and sign a different snapshot
 * after the user has reviewed this one. The [handle] is owned by wallet2 and is valid
 * only while the owning wallet remains open.
 */
class UnsignedTransaction internal constructor(override var handle: Long) : StagingTransaction {
    internal fun invalidate() {
        handle = 0L
    }

    enum class Status {
        Status_Ok,
        Status_Error,
        Status_Critical
    }

    enum class Priority(val value: Int) {
        Priority_Default(0),
        Priority_Low(1),
        Priority_Medium(2),
        Priority_High(3),
        Priority_Last(4);


        companion object {
            fun fromInteger(n: Int): Priority? {
                when (n) {
                    0 -> return Priority_Default
                    1 -> return Priority_Low
                    2 -> return Priority_Medium
                    3 -> return Priority_High
                }
                return null
            }
        }
    }

    val status: Status
        get() = Status.entries[statusJ]

    val statusJ: Int
        external get

    val errorString: String?
        external get

    val txCount: Long
        external get


    val address: String?
        external get

    val amount: Long
        // commit transaction or save to file if filename is provided.
        external get

    val fee: Long
        //    public native long getDust();
        external get

    val firstTxId: String
        get() = firstTxIdJ ?: ""

    // nullable: the JNI returns nullptr for a set with no payment ids; the old
    // non-null declaration made that an NPE on access
    val firstTxIdJ: String?
        external get

    /*
     * wallet2's summary of what sign() will produce, built at load time from the real
     * outputs (splitted_dsts) rather than the advisory dests the scalar getters read.
     * It is the text monero-wallet-cli shows before signing.
     */
    val confirmationMessage: String
        get() = confirmationMessageJ() ?: ""

    private external fun confirmationMessageJ(): String?

    /*
     * Signs THIS loaded object to [outputPath]. Wallet.signAndExportJ re-loads the
     * unsigned file from disk and signs whatever is there; this signs what was reviewed.
     */
    fun sign(outputPath: String): Boolean = signJ(outputPath)

    private external fun signJ(outputPath: String): Boolean

    companion object {
        init {
            System.loadLibrary("anonero")
        }
    }
}
