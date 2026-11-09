package io.anonero.model

/**
 * A wallet2 pending transaction, before or after commit.
 *
 * The native object is created by `Wallet.createTransaction*` and is owned by wallet2.
 * [handle] is a borrowed pointer; the wallet releases it through `Wallet.disposeTransaction`
 * when the wallet replaces or closes the pending transaction. A transaction with a non-OK
 * [status] must not be committed or treated as broadcast.
 */
class PendingTransaction internal constructor(override var handle: Long) : StagingTransaction {
    internal fun invalidate() {
        handle = 0L
    }

    val status: Status
        get() = Status.values()[getStatusJ()]

    external fun getStatusJ(): Int
    external fun getErrorString(): String?

    // commit transaction or save to file if filename is provided.
    external fun commit(filename: String?, overwrite: Boolean): Boolean

    external fun getAmount(): Long
    external fun getDust(): Long
    external fun getFee(): Long
    external fun getFirstTxIdJ(): String?
    external fun getTxCount(): Long

    enum class Status {
        Status_Ok, Status_Error, Status_Critical
    }

    enum class Priority(value: Int) {
        Priority_Default(0), Priority_Low(1), Priority_Medium(2), Priority_High(3), Priority_Last(4);

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

    companion object {
        init {
            System.loadLibrary("anonero")
        }
    }
}