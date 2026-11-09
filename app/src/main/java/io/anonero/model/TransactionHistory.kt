package io.anonero.model

import timber.log.Timber

private const val TAG = "TransactionHistory"

/*
 * Wallet-owned transaction list for one account.
 *
 * [handle] is a `jlong` borrowed from wallet2; it is valid only while the owning
 * [Wallet] is open. [refreshWithNotes] pulls the native list, drops transactions whose
 * account does not match [accountIndex], and reloads user notes from wallet2.
 */
class TransactionHistory(private val handle: Long, var accountIndex: Int) {
    var all: List<TransactionInfo> = ArrayList()
        private set

    fun setAccountFor(wallet: Wallet) {
        if (accountIndex != wallet.getAccountIndex()) {
            accountIndex = wallet.getAccountIndex()
            refreshWithNotes(wallet)
        }
    }

    private fun loadNotes(wallet: Wallet) {
        for (info in all) {
            info.notes = wallet.getUserNote(info.hash)
        }
    }

    external fun getCount(): Int

    fun refreshWithNotes(wallet: Wallet) {
        refresh()
        loadNotes(wallet)
    }

    private fun refresh() {
        val transactionInfos = refreshJ()
        Timber.tag(TAG).d("refresh size=%s", transactionInfos.size)
        val iterator = transactionInfos.iterator()
        while (iterator.hasNext()) {
            val info = iterator.next()
            if (info.accountIndex != accountIndex) {
                iterator.remove()
            }
        }
        all = transactionInfos
    }

    private external fun refreshJ(): MutableList<TransactionInfo>

    companion object {
        init {
            System.loadLibrary("anonero")
        }
    }
}
