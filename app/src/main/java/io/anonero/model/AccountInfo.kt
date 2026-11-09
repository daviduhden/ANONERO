package io.anonero.model

/** A Monero subaddress account inside one wallet file (not a separate wallet). */
data class AccountInfo(
    val index: Int,
    val label: String,
    val address: String,
    val balance: Long,
    val unlockedBalance: Long,
)
