package io.anonero.model

class UnsignedTransaction internal constructor(override var handle: Long) : StagingTransaction {
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

    /**
     * wallet2's summary of what sign() will produce, built at load time from the real
     * outputs (splitted_dsts) rather than the advisory dests the scalar getters read.
     * It is the text monero-wallet-cli shows before signing.
     */
    val confirmationMessage: String
        get() = confirmationMessageJ() ?: ""

    private external fun confirmationMessageJ(): String?

    /**
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
