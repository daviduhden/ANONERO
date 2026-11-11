package io.anonero.model

/**
 * Wallet2 coin-control view for the active wallet.
 *
 * [all] is a snapshot of unspent outputs as reported by the native `Coins` API. The
 * key image (`CoinsInfo.key`) is used for transaction construction; [freeze] and [thaw]
 * use the output public key instead. Both operations re-read the native list so callers
 * never act on a stale freeze flag.
 *
 * The [handle] is borrowed from wallet2 and is valid only while the owning wallet is open.
 */
class Coins(
    private val handle: Long,
) {
    var all: List<CoinsInfo> = ArrayList()
        private set

    fun refresh() {
        val transactionInfos = refreshJ()
        all = transactionInfos
    }

    private external fun refreshJ(): List<CoinsInfo>

    external fun getCount(): Int

    // Freeze by output public key (not key image), then re-read wallet2's state.
    fun freeze(publicKey: String) {
        setFrozenJ(publicKey)
        refresh()
    }

    // Thaw by output public key, then re-read wallet2's state.
    fun thaw(publicKey: String) {
        thawJ(publicKey)
        refresh()
    }

    private external fun setFrozenJ(publicKey: String)

    private external fun thawJ(publicKey: String)

    companion object {
        init {
            System.loadLibrary("anonero")
        }
    }
}
