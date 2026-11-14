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

package io.anonero.model.node

import io.anonero.AnonConfig
import io.anonero.model.NetworkType
import io.anonero.model.WalletManager
import io.anonero.network.NodeNetwork
import kotlinx.serialization.Serializable
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.UnknownHostException

enum class NodeFields(
    val value: String,
) {
    RPC_HOST("host"),
    RPC_PORT("rpcPort"),
    RPC_USERNAME("username"),
    RPC_PASSWORD("passphrase"),
    RPC_NETWORK("network"),
    NODE_TRUSTED("trusted"),
    NODE_TLS("tls"),
    NODE_NAME("name"),
    ;

    override fun toString(): String = value
}

/**
 * A saved Monero daemon or LWS endpoint.
 *
 * [host] is stored without a scheme; [rpcPort] is the wallet RPC port. [networkType] must
 * match the build's network. [trusted] is legacy metadata and is not the live trust flag
 * used for LWS; the wallet layer sets trust explicitly on the native wallet.
 *
 * The string form is `user:password@host:port/network/name`, with optional credentials
 * and label. Parsing is intentionally strict because the value can be restored from a
 * backup or pasted by a user.
 */
@Serializable
open class Node {
    var networkType: NetworkType? = null
    var rpcPort = 0
    var name: String? = null
        private set
    var host: String? = null
    private var levinPort = 0
    var username = ""
    var password = ""
    var trusted = true
        private set
    var useTls = false
        private set

    internal constructor(nodeString: String?) {
        require(!nodeString.isNullOrEmpty()) { "daemon is empty" }
        var daemonAddress: String
        val a = nodeString.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        when (a.size) {
            1 -> { // no credentials
                daemonAddress = a[0]
                this.username = ""
                this.password = ""
            }

            2 -> { // credentials
                val userPassword =
                    a[0].split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                require(userPassword.size == 2) { "User:Password invalid" }
                this.username = userPassword[0]
                this.password =
                    if (this.username.isNotEmpty()) {
                        userPassword[1]
                    } else {
                        ""
                    }
                daemonAddress = a[1]
            }

            else -> {
                throw IllegalArgumentException("Too many @")
            }
        }

        val daParts =
            daemonAddress.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        require(!(daParts.size > 3 || daParts.isEmpty())) { "Too many '/' or too few" }
        daemonAddress = daParts[0]
        val (host, parsedPort) = parseDaemonAddress(daemonAddress)

        this.networkType =
            if (daParts.size == 1) {
                NetworkType.NetworkType_Mainnet
            } else {
                when (daParts[1]) {
                    MAINNET -> NetworkType.NetworkType_Mainnet
                    STAGENET -> NetworkType.NetworkType_Stagenet
                    TESTNET -> NetworkType.NetworkType_Testnet
                    else -> AnonConfig.getNetworkType()
                }
            }

//        require(networkType == WalletManager.instance?.networkType) { "wrong net: $networkType" }
        var name: String? = host
        if (daParts.size == 3) {
            try {
                name = URLDecoder.decode(daParts[2], "UTF-8")
            } catch (ex: UnsupportedEncodingException) {
                Timber.tag("Node.kt").w(ex) // if we can't encode it, we don't use it
            }
        }
        this.name = name
        try {
            this.host = host
        } catch (ex: UnknownHostException) {
            throw IllegalArgumentException("cannot resolve host $host")
        }
        this.rpcPort = parsedPort
        this.levinPort = this.defaultLevinPort
    }

    internal constructor(jsonObject: JSONObject?) {
        requireNotNull(jsonObject) { "daemon is empty" }
        if (jsonObject.has(NodeFields.RPC_USERNAME.value)) {
            username = jsonObject.getString(NodeFields.RPC_USERNAME.value)
        }
        if (jsonObject.has(NodeFields.RPC_PASSWORD.value)) {
            password = jsonObject.getString(NodeFields.RPC_PASSWORD.value)
        }
        if (jsonObject.has(NodeFields.RPC_HOST.value)) {
            this.host = jsonObject.getString(NodeFields.RPC_HOST.value)
        }
        this.rpcPort =
            if (jsonObject.has(NodeFields.RPC_PORT.value)) {
                jsonObject.getInt(NodeFields.RPC_PORT.value)
            } else {
                defaultRpcPort
            }
        if (jsonObject.has(NodeFields.NODE_NAME.value)) {
            this.name = jsonObject.getString(NodeFields.NODE_NAME.value)
        }
        if (jsonObject.has(NodeFields.RPC_NETWORK.value)) {
            networkType =
                when (jsonObject.getString(NodeFields.RPC_NETWORK.value)) {
                    MAINNET -> NetworkType.NetworkType_Mainnet

                    STAGENET -> NetworkType.NetworkType_Stagenet

                    TESTNET -> NetworkType.NetworkType_Testnet

                    else -> throw IllegalArgumentException(
                        "invalid net: " +
                            jsonObject.getString(
                                NodeFields.RPC_NETWORK.value,
                            ),
                    )
                }
            require(networkType == WalletManager.instance?.networkType) { "wrong net: $networkType" }
        }
        if (jsonObject.has(NodeFields.NODE_TRUSTED.value)) {
            this.trusted = jsonObject.getBoolean(NodeFields.NODE_TRUSTED.value)
        }
        if (jsonObject.has(NodeFields.NODE_TLS.value)) {
            this.useTls = jsonObject.getBoolean(NodeFields.NODE_TLS.value)
        }
    }

    constructor() {
        networkType = WalletManager.instance?.networkType
    }

    // constructor used for created nodes from retrieved peer lists
    constructor(socketAddress: InetSocketAddress) : this() {
        host = socketAddress.hostString
        rpcPort = 0 // unknown
        levinPort = socketAddress.port
        username = ""
        password = ""
    }

    constructor(anotherNode: Node) {
        networkType = anotherNode.networkType
        overwriteWith(anotherNode)
    }

    override fun hashCode(): Int = host.orEmpty().hashCode()

    // Nodes are equal if they are the same host address:port & are on the same network
    override fun equals(other: Any?): Boolean {
        if (other !is Node) return false
        return trusted == other.trusted && useTls == other.useTls && host == other.host &&
            address == other.address && rpcPort == other.rpcPort &&
            networkType == other.networkType &&
            username == other.username &&
            password == other.password
    }

    fun toNodeString(): String = rpcUrl

    fun toJson(): JSONObject {
        val jsonObject = JSONObject()
        try {
            jsonObject.put("username", username)
            jsonObject.put("password", password)
            jsonObject.put("host", host)
            jsonObject.put("rpcPort", rpcPort)
            when (networkType) {
                NetworkType.NetworkType_Mainnet -> jsonObject.put("network", MAINNET)
                NetworkType.NetworkType_Stagenet -> jsonObject.put("network", STAGENET)
                NetworkType.NetworkType_Testnet -> jsonObject.put("network", TESTNET)
                null -> TODO()
            }
            if (name?.isNotEmpty() == true) jsonObject.put("name", name)
            jsonObject.put("trusted", trusted)
            if (useTls) jsonObject.put(NodeFields.NODE_TLS.value, true)
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        return jsonObject
    }

    override fun toString(): String {
        val sb = StringBuilder()
        if (username.isNotEmpty() && password.isNotEmpty()) {
            sb
                .append(username)
                .append(":")
                .append(password)
                .append("@")
        }
        sb.append(hostPort)
        sb.append("/")
        when (networkType) {
            NetworkType.NetworkType_Mainnet -> sb.append(MAINNET)
            NetworkType.NetworkType_Stagenet -> sb.append(STAGENET)
            NetworkType.NetworkType_Testnet -> sb.append(TESTNET)
            null -> TODO()
        }
        if (name != null) {
            try {
                sb.append("/").append(URLEncoder.encode(name, "UTF-8"))
            } catch (ex: UnsupportedEncodingException) {
                Timber.tag("Node.kt").w(ex) // if we can't encode it, we don't store it
            }
        }
        return sb.toString()
    }

    val hostPort: String
        get() {
            val value = host.orEmpty()
            val formatted = if (value.contains(':') && !value.startsWith("[")) "[$value]" else value
            return "$formatted:$rpcPort"
        }

    val address: String get() = hostPort

    /** wallet2 daemon URL; TLS is opt-in per node. */
    val rpcUrl: String get() = "${if (useTls) "https" else "http"}://$hostPort"

    val network: NodeNetwork get() = NodeNetwork.fromHost(host)

    private fun overwriteWith(anotherNode: Node) {
        check(networkType == anotherNode.networkType) { "network types do not match" }
        name = anotherNode.name
        host = anotherNode.host
        rpcPort = anotherNode.rpcPort
        levinPort = anotherNode.levinPort
        username = anotherNode.username
        password = anotherNode.password
        useTls = anotherNode.useTls
        trusted = anotherNode.trusted
    }

    private fun parseDaemonAddress(address: String): Pair<String, Int> {
        if (address.startsWith("[")) {
            val end = address.indexOf(']')
            require(end > 1) { "Invalid IPv6 host" }
            val host = address.substring(1, end)
            val rest = address.substring(end + 1)
            val port =
                if (rest.isEmpty()) {
                    defaultRpcPort
                } else {
                    require(rest.startsWith(":")) { "Port not numeric" }
                    rest.drop(1).toIntOrNull() ?: throw IllegalArgumentException("Port not numeric")
                }
            return host to port
        }
        val colonCount = address.count { it == ':' }
        return when (colonCount) {
            0 -> {
                address to defaultRpcPort
            }

            1 -> {
                val separator = address.lastIndexOf(':')
                require(separator > 0) { "Invalid host" }
                val port =
                    address.substring(separator + 1).toIntOrNull()
                        ?: throw IllegalArgumentException("Port not numeric")
                address.substring(0, separator) to port
            }

            else -> {
                throw IllegalArgumentException("IPv6 hosts must be bracketed")
            }
        }
    }

    companion object {
        const val MAINNET = "mainnet"
        const val STAGENET = "stagenet"
        const val TESTNET = "testnet"
        private var defaultLevinPortCache = 0
        private var defaultRpcPortCache = 0

        val defaultRpcPort: Int
            // every node knows its network, but they are all the same
            get() {

                if (defaultRpcPortCache > 0) return defaultRpcPortCache
                defaultRpcPortCache =
                    when (WalletManager.instance?.networkType) {
                        NetworkType.NetworkType_Mainnet -> 18081
                        NetworkType.NetworkType_Testnet -> 28081
                        NetworkType.NetworkType_Stagenet -> 38081
                        else -> throw IllegalStateException("unsupported net " + WalletManager.instance?.networkType)
                    }
                return defaultRpcPortCache
            }

        fun fromString(nodeString: String?): Node? =
            try {
                Node(nodeString)
            } catch (ex: IllegalArgumentException) {
                Timber.tag("Node.kt").w(ex)
                null
            }

        fun fromJson(jsonObject: JSONObject?): Node? =
            try {
                Node(jsonObject)
            } catch (ex: IllegalArgumentException) {
                Timber.tag("Node.kt").w(ex)
                null
            } catch (ex: UnknownHostException) {
                Timber.tag("Node.kt").w(ex)
                null
            } catch (ex: JSONException) {
                Timber.tag("Node.kt").w(ex)
                null
            }
    }

    private val defaultLevinPort: Int
        // every node knows its network, but they are all the same
        get() {
            if (defaultLevinPortCache > 0) return defaultLevinPortCache
            defaultLevinPortCache =
                when (WalletManager.instance?.networkType) {
                    NetworkType.NetworkType_Mainnet -> 18080
                    NetworkType.NetworkType_Testnet -> 28080
                    NetworkType.NetworkType_Stagenet -> 38080
                    else -> throw IllegalStateException("unsupported net " + WalletManager.instance?.networkType)
                }
            return defaultLevinPortCache
        }
}
