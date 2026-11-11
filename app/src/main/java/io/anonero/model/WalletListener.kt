package io.anonero.model

/**
 * Callbacks invoked by the native wallet listener.
 *
 * The native adapter attaches the calling wallet2 refresh thread to the JVM and invokes
 * these methods there. They must be short, must not block, and must not assume they run on
 * the Android main thread. The adapter currently delivers [updated], [refreshed],
 * [newBlock], and [unconfirmedMoneyReceived]; [moneySpent] and [moneyReceived] are kept
 * for interface compatibility and are only logged by the C++ side.
 */
interface WalletListener {
    /**
     * Called by wallet2 when an outgoing transaction is detected.
     *
     * @param txId transaction id, when wallet2 provides one.
     * @param amount amount in atomic units.
     */
    fun moneySpent(
        txId: String?,
        amount: Long,
    )

    /**
     * Called by wallet2 when an incoming transaction is detected.
     *
     * @param txId transaction id, when wallet2 provides one.
     * @param amount amount in atomic units.
     */
    fun moneyReceived(
        txId: String?,
        amount: Long,
    )

    /**
     * Called when a payment is first seen in the transaction pool.
     *
     * @param txId transaction id.
     * @param amount amount in atomic units.
     */
    fun unconfirmedMoneyReceived(
        txId: String?,
        amount: Long,
    )

    /**
     * Called when wallet2 observes a new block.
     *
     * @param height block height reported by the wallet.
     */
    fun newBlock(height: Long)

    /**
     * Generic wallet-change callback (sent, received, new block, or refresh state).
     */
    fun updated()

    /**
     * Called after a refresh completes. This is the main signal used to re-read history,
     * coins, and sync progress.
     */
    fun refreshed()
}
