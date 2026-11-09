package io.anonero.model

class Coins(private val handle: Long){
    var all: List<CoinsInfo> = ArrayList()
        private set

    fun refresh() {
        val transactionInfos = refreshJ()
        all = transactionInfos
    }

    private external fun refreshJ(): List<CoinsInfo>
    external fun getCount(): Int

    /** Freeze by output public key (not key image), then re-read wallet2's state. */
    fun freeze(publicKey: String) {
        setFrozenJ(publicKey)
        refresh()
    }

    /** Thaw by output public key, then re-read wallet2's state. */
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