package io.anonero.model.node

import io.anonero.model.Wallet

data class DaemonInfo(
    val daemon: String?,
    val connectionStatus: Wallet.ConnectionStatus,
    val daemonHeight: Long,
)
