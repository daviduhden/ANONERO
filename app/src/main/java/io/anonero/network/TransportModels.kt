/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
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

package io.anonero.network

import android.content.SharedPreferences
import androidx.core.content.edit
import io.anonero.model.node.NodeFields
import io.anonero.util.WALLET_PROXY
import io.anonero.util.WALLET_PROXY_PORT
import io.anonero.util.WALLET_USE_TOR
import java.net.InetAddress

/**
 * Destination network of a Monero RPC endpoint, not the P2P network.
 *
 * The value is derived from the host and must never be inferred from the proxy type:
 * a generic SOCKS5 proxy does not imply Tor or I2P reachability.
 */
enum class NodeNetwork {
    CLEARNET,
    ONION,
    I2P,
    ;

    companion object {
        fun fromHost(host: String?): NodeNetwork {
            val normalized =
                host
                    .orEmpty()
                    .trim()
                    .trim('[', ']')
                    .lowercase()
                    .trimEnd('.')
            return when {
                normalized.endsWith(".onion") -> ONION
                normalized.endsWith(".i2p") -> I2P
                else -> CLEARNET
            }
        }
    }
}

/** User-selected connectivity provider. Embedded Tor is the compatibility default. */
enum class TransportMode {
    EMBEDDED_TOR,
    ORBOT,
    INVIZIBLE_TOR,
    SYSTEM_VPN,
    I2PD,
    INVIZIBLE_I2P,
    I2P_JAVA,
    CUSTOM_PROXY,
    ;

    val usesEmbeddedTor: Boolean get() = this == EMBEDDED_TOR

    val isTor: Boolean get() = this == EMBEDDED_TOR || this == ORBOT || this == INVIZIBLE_TOR

    val isI2p: Boolean
        get() = this == I2PD || this == INVIZIBLE_I2P || this == I2P_JAVA

    val requiresProxy: Boolean get() = this != SYSTEM_VPN

    val fixedNodeNetwork: NodeNetwork?
        get() =
            when (this) {
                EMBEDDED_TOR, ORBOT, INVIZIBLE_TOR -> NodeNetwork.ONION
                SYSTEM_VPN -> NodeNetwork.CLEARNET
                I2PD, INVIZIBLE_I2P, I2P_JAVA -> NodeNetwork.I2P
                CUSTOM_PROXY -> null
            }
}

/** Proxy endpoint accepted by wallet2. Only unauthenticated SOCKS5 is supported today. */
data class ProxyEndpoint(
    val host: String,
    val port: Int,
) {
    val formatted: String
        get() {
            val clean = host.trim().trim('[', ']')
            val wrapped = if (':' in clean && !clean.startsWith("[")) "[$clean]" else clean
            // wallet2 defaults an unqualified endpoint to SOCKS4a; I2P proxies and
            // remote .i2p resolution require SOCKS5.
            return "socks5://$wrapped:$port"
        }

    companion object {
        fun normalizeHost(host: String): String = host.trim().trim('[', ']')

        fun isNumericOrLoopback(host: String): Boolean {
            val clean = normalizeHost(host)
            if (clean.isEmpty()) return false
            if (clean == "localhost") return true
            if (clean.contains(':')) {
                // Avoid a DNS lookup for malformed or hostname-like values.
                return clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it in ":.%" }
            }
            return clean.all { it.isDigit() || it == '.' } && clean.count { it == '.' } == 3
        }

        fun isLoopback(host: String): Boolean {
            val clean = normalizeHost(host).lowercase()
            if (clean == "localhost" || clean == "::1") return true
            if (!clean.startsWith("127.")) return false
            return runCatching { InetAddress.getByName(clean).isLoopbackAddress }.getOrDefault(false)
        }
    }
}

/** Endpoint produced by a transport provider. Direct means no proxy (system VPN). */
sealed interface TransportEndpoint {
    data class Socks5(
        val endpoint: ProxyEndpoint,
    ) : TransportEndpoint

    data object Direct : TransportEndpoint
}

enum class TransportFailure {
    INVALID_CONFIGURATION,
    EMBEDDED_TOR_TIMEOUT,
    EXTERNAL_PROXY_UNREACHABLE,
    VPN_INACTIVE,
    PROXY_REJECTED,
    TRANSPORT_CHANGED,
    I2P_APP_MISSING,
    INTERNAL_ERROR,
}

class TransportUnavailableException(
    val failure: TransportFailure,
) : Exception(failure.name)

enum class TransportConfigError {
    INVALID_PROXY_HOST,
    INVALID_PROXY_PORT,
    EXTERNAL_PROXY_REQUIRED,
    EXTERNAL_PROXY_NOT_LOOPBACK,
    CUSTOM_NETWORK_REQUIRED,
}

/**
 * Persisted transport policy.
 *
 * [customNodeNetwork] is only meaningful for [TransportMode.CUSTOM_PROXY]; the fixed
 * modes derive their network from [TransportMode.fixedNodeNetwork]. Automatic node
 * selection and validation both use [nodeNetwork], so the two cannot disagree.
 */
data class TransportConfig(
    val mode: TransportMode = TransportMode.EMBEDDED_TOR,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val customNodeNetwork: NodeNetwork = NodeNetwork.CLEARNET,
    val autoSelectNode: Boolean = true,
    val preferTrustedNode: Boolean = true,
) {
    val nodeNetwork: NodeNetwork
        get() = mode.fixedNodeNetwork ?: customNodeNetwork

    val proxyEndpoint: ProxyEndpoint?
        get() =
            if (mode.requiresProxy && proxyHost.isNotBlank() && proxyPort in 1..65535) {
                ProxyEndpoint(ProxyEndpoint.normalizeHost(proxyHost), proxyPort)
            } else {
                null
            }

    fun validationError(): TransportConfigError? {
        if (!mode.requiresProxy) return null
        val host = ProxyEndpoint.normalizeHost(proxyHost)
        if (host.isBlank()) return TransportConfigError.EXTERNAL_PROXY_REQUIRED
        if (!ProxyEndpoint.isNumericOrLoopback(host)) {
            return TransportConfigError.INVALID_PROXY_HOST
        }
        if (proxyPort !in 1..65535) return TransportConfigError.INVALID_PROXY_PORT
        if (mode != TransportMode.CUSTOM_PROXY && !ProxyEndpoint.isLoopback(host)) {
            // Orbot, InviZible and i2pd are local proxies. A remote endpoint would
            // require a direct connection to it and is not treated as a Tor/I2P path.
            return TransportConfigError.EXTERNAL_PROXY_NOT_LOOPBACK
        }
        return null
    }

    /**
     * True when the provider endpoint or destination network changed and the cached
     * provider must be discarded. Preference-only changes do not release it.
     */
    fun requiresProviderRelease(next: TransportConfig): Boolean =
        mode != next.mode ||
            proxyHost != next.proxyHost ||
            proxyPort != next.proxyPort ||
            customNodeNetwork != next.customNodeNetwork

    fun save(prefs: SharedPreferences) {
        prefs.edit(commit = true) {
            putString(PREFS_TRANSPORT_MODE, mode.name)
            putString(PREFS_TRANSPORT_HOST, proxyHost)
            putInt(PREFS_TRANSPORT_PORT, proxyPort)
            putString(PREFS_TRANSPORT_NETWORK, customNodeNetwork.name)
            putBoolean(PREFS_NODE_AUTO_SELECT, autoSelectNode)
            putBoolean(PREFS_NODE_PREFER_TRUSTED, preferTrustedNode)

            // Keep the legacy keys coherent for older readers and downgrade safety.
            putBoolean(WALLET_USE_TOR, mode.usesEmbeddedTor)
            when {
                mode.usesEmbeddedTor || mode == TransportMode.SYSTEM_VPN -> {
                    remove(WALLET_PROXY)
                    remove(WALLET_PROXY_PORT)
                }

                else -> {
                    proxyEndpoint?.let {
                        putString(WALLET_PROXY, it.host)
                        putInt(WALLET_PROXY_PORT, it.port)
                    }
                }
            }
        }
    }

    companion object {
        fun load(prefs: SharedPreferences): TransportConfig {
            val mode =
                prefs
                    .getString(PREFS_TRANSPORT_MODE, null)
                    ?.let { runCatching { TransportMode.valueOf(it) }.getOrNull() }
                    ?: migrateMode(prefs)
            val migrated = migrateCustomNetwork(prefs, mode)
            return TransportConfig(
                mode = mode,
                proxyHost = prefs.getString(PREFS_TRANSPORT_HOST, migrated.first).orEmpty(),
                proxyPort = prefs.getInt(PREFS_TRANSPORT_PORT, migrated.second),
                customNodeNetwork =
                    prefs
                        .getString(PREFS_TRANSPORT_NETWORK, null)
                        ?.let { runCatching { NodeNetwork.valueOf(it) }.getOrNull() }
                        ?: NodeNetwork.fromHost(prefs.getString(NodeFields.RPC_HOST.value, "")),
                autoSelectNode = prefs.getBoolean(PREFS_NODE_AUTO_SELECT, true),
                preferTrustedNode = prefs.getBoolean(PREFS_NODE_PREFER_TRUSTED, true),
            )
        }

        private fun migrateMode(prefs: SharedPreferences): TransportMode {
            val legacyUseTor = prefs.getBoolean(WALLET_USE_TOR, true)
            val legacyHost = prefs.getString(WALLET_PROXY, "").orEmpty()
            val legacyPort = prefs.getInt(WALLET_PROXY_PORT, -1)
            return if (!legacyUseTor && legacyHost.isNotBlank() && legacyPort in 1..65535) {
                TransportMode.CUSTOM_PROXY
            } else {
                // A legacy "Tor disabled, no proxy" configuration was unusable and
                // failed closed; embed Tor instead of guessing a direct path.
                TransportMode.EMBEDDED_TOR
            }
        }

        private fun migrateCustomNetwork(
            prefs: SharedPreferences,
            mode: TransportMode,
        ): Pair<String, Int> {
            if (mode != TransportMode.CUSTOM_PROXY) return "" to 0
            val host = prefs.getString(PREFS_TRANSPORT_HOST, null)
            val port = prefs.getInt(PREFS_TRANSPORT_PORT, 0)
            if (!host.isNullOrBlank() && port in 1..65535) return host to port
            return prefs.getString(WALLET_PROXY, "").orEmpty() to prefs.getInt(WALLET_PROXY_PORT, 0)
        }
    }
}

const val PREFS_TRANSPORT_MODE = "networkTransportMode"
const val PREFS_TRANSPORT_HOST = "networkTransportHost"
const val PREFS_TRANSPORT_PORT = "networkTransportPort"
const val PREFS_TRANSPORT_NETWORK = "networkTransportNodeNetwork"
const val PREFS_NODE_AUTO_SELECT = "networkNodeAutoSelect"
const val PREFS_NODE_PREFER_TRUSTED = "networkNodePreferTrusted"

enum class TransportStatus {
    STOPPED,
    READY,
    UNAVAILABLE,
    ERROR,
}

data class TransportState(
    val mode: TransportMode,
    val status: TransportStatus,
    val endpoint: TransportEndpoint? = null,
    val failure: TransportFailure? = null,
    val vpnActive: Boolean = false,
)
