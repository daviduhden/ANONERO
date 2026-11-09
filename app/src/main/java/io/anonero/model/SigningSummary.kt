package io.anonero.model

import java.math.BigDecimal

/**
 * wallet2's load-time summary of an unsigned set, parsed into review rows.
 *
 * UnsignedTransaction.confirmationMessage is built by checkLoadedTx from splitted_dsts --
 * the outputs sign() actually constructs -- and is the sentence monero-wallet-cli asks the
 * user to confirm. Its format is fixed and untranslated (wallet2_api's tr() is the identity):
 *
 *   Loaded N transactions, for <inputs>, fee <fee>, sending <amt> to <addr>[, sending ...],
 *   <amt> change to <addr> | no change, with min ring size R. [K outputs to import. ]
 *
 * Anything that does not match exactly, or whose amounts do not add up, parses to null and
 * the caller shows the raw sentence instead. Rows are never built from any other source.
 */
data class SigningSummary(
    val txCount: Long,
    val destinations: List<Destination>,
    /** Sum paid to [destinations], excluding fee and change. */
    val amount: Long,
    val fee: Long,
    val change: Destination?,
    val minRingSize: Long,
) {
    data class Destination(val address: String, val amount: Long)

    companion object {
        private const val AMOUNT = """(\d+\.\d{12})"""
        private const val ADDRESS = """([1-9A-HJ-NP-Za-km-z]+)"""
        private val MESSAGE = Regex(
            """^Loaded (\d+) transactions, for $AMOUNT, fee $AMOUNT, (.+?), """ +
                """(?:$AMOUNT change to $ADDRESS|no change), with min ring size (\d+)\.""" +
                """(?: \d+ outputs to import\.)?\s*$"""
        )
        private const val PAYMENT_ID = """([0-9a-f]{16})"""
        private const val DUMMY_PAYMENT_ID = "0000000000000000"
        // a standard address prints as "<integrated> (<standard> with encrypted payment id <hex>)"
        private val SENDING = Regex(
            """^sending $AMOUNT to $ADDRESS""" +
                """(?: \($ADDRESS with encrypted payment id $PAYMENT_ID\))?$"""
        )

        fun parse(message: String?): SigningSummary? {
            if (message.isNullOrEmpty()) return null
            val m = MESSAGE.find(message) ?: return null
            val (txCount, inputs, fee, dests, changeAmount, changeAddress, ring) = m.destructured
            return runCatching {
                val destinations = dests.split(", ").map { part ->
                    val d = SENDING.find(part) ?: return null
                    val (sent, shown, standard, paymentId) = d.destructured
                    // all-zero id is the dummy: show the standard address
                    Destination(if (paymentId == DUMMY_PAYMENT_ID) standard else shown, atomic(sent))
                }.filter { it.amount > 0 } // drops the zero-amount decoy of a send with no change
                if (destinations.isEmpty()) return null
                val amount = destinations.fold(0L) { acc, d -> Math.addExact(acc, d.amount) }
                val feeAtomic = atomic(fee)
                val change = if (changeAmount.isEmpty()) null
                else Destination(changeAddress, atomic(changeAmount))
                // the sentence must add up: inputs == paid + fee + change
                val spent = Math.addExact(Math.addExact(amount, feeAtomic), change?.amount ?: 0L)
                if (spent != atomic(inputs)) return null
                val count = txCount.toLong()
                if (count < 1) return null
                SigningSummary(count, destinations, amount, feeAtomic, change, ring.toLong())
            }.getOrNull()
        }

        /** "1.200000000000" -> 1200000000000 atomic units; throws on anything else. */
        private fun atomic(s: String): Long = BigDecimal(s).movePointRight(12).longValueExact()
    }
}
