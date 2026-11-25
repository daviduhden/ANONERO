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

/**
 * Callbacks invoked by the native wallet listener.
 *
 * The native adapter obtains a JNI environment for the calling wallet2 thread and invokes
 * [updated], [refreshed], [newBlock], and [unconfirmedMoneyReceived] synchronously there.
 * Keep work on this path bounded and publish UI updates through the wallet state flows.
 * The adapter discards the native [moneySpent] and [moneyReceived] events, including their
 * transaction IDs and amounts.
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
