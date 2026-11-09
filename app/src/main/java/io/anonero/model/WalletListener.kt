package io.anonero.model

/*
 * Callbacks invoked by the native wallet listener.
 *
 * Callbacks run on wallet2's refresh thread after the native adapter attaches it to the
 * JVM. They must be short, must not block, and must not assume they are on the Android
 * main thread. `moneySpent` and `moneyReceived` are declared here for compatibility but
 * the current C++ adapter only logs those events and does not call the Kotlin methods.
 */
interface WalletListener {
    /*
     * moneySpent - called when money spent
     *
     * @param txId   - transaction id
     * @param amount - tvAmount
     */
    fun moneySpent(txId: String?, amount: Long)

    /*
     * moneyReceived - called when money received
     *
     * @param txId   - transaction id
     * @param amount - tvAmount
     */
    fun moneyReceived(txId: String?, amount: Long)

    /*
     * unconfirmedMoneyReceived - called when payment arrived in tx pool
     *
     * @param txId   - transaction id
     * @param amount - tvAmount
     */
    fun unconfirmedMoneyReceived(txId: String?, amount: Long)

    /*
     * newBlock      - called when new block received
     *
     * @param height - block height
     */
    fun newBlock(height: Long)

    /*
     * updated  - generic callback, called when any event (sent/received/block reveived/etc) happened with the wallet;
     */
    fun updated()

    /*
     * refreshed - called when wallet refreshed by background thread or explicitly refreshed by calling "refresh" synchronously
     */
    fun refreshed()
}
