package io.anonero.model

/**
 * One wallet output as reported by wallet2 through the patched Coins API.
 *
 * [key] is the key image (used by coin control), [pub_key] is the output public key
 * (used by the native freeze/thaw API). [frozen] is a wallet2 flag and is distinct from
 * spent, locked by unlock time, or unavailable because of confirmations.
 */
data class CoinsInfo(
    /* wallet2 global output index (historical field name kept for compatibility). */
    val unlockTime: Long,
    val spent: Boolean,
    val key: String,
    val amount: Long,
    val hash: String,
    val pub_key: String,
    val frozen: Boolean,
    /* internal output index in the transaction (historical field name). */
    val creationTime: Long,
    val unlocked: Boolean = false,
    val subaddrAccount: Int = 0,
    val subaddrIndex: Int = 0,
    val blockHeight: Long = 0,
    val address: String = "",
    val addressLabel: String = "",
    val keyImageKnown: Boolean = false,
) : Comparable<CoinsInfo> {

    override fun compareTo(other: CoinsInfo): Int {
        val b1: Long = this.amount
        val b2 = other.amount
        if (b1 > b2) {
            return -1
        } else if (b1 < b2) {
            return 1
        } else {
            return this.hash.compareTo(other.hash)
        }
    }
}
