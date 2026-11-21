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
import io.anonero.network.NodeNetwork
import kotlinx.serialization.Serializable
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.net.InetSocketAddress

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

    private constructor(jsonObject: JSONObject?) : this() {
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
                    else -> throw IllegalArgumentException("Invalid node network")
                }
            require(networkType == AnonConfig.getNetworkType()) { "Node network does not match the build" }
        }
        if (jsonObject.has(NodeFields.NODE_TRUSTED.value)) {
            this.trusted = jsonObject.getBoolean(NodeFields.NODE_TRUSTED.value)
        }
        if (jsonObject.has(NodeFields.NODE_TLS.value)) {
            this.useTls = jsonObject.getBoolean(NodeFields.NODE_TLS.value)
        }
    }

    constructor() {
        // Keep the model testable without loading the native wallet library. The build
        // network is the same value WalletManager initializes from.
        networkType = AnonConfig.getNetworkType()
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
            jsonObject.put("network", checkNotNull(networkType) { "Node network is missing" }.toString())
            if (name?.isNotEmpty() == true) jsonObject.put("name", name)
            jsonObject.put("trusted", trusted)
            if (useTls) jsonObject.put(NodeFields.NODE_TLS.value, true)
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
        return jsonObject
    }

    override fun toString(): String = "Node"

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

    companion object {
        const val MAINNET = "mainnet"
        const val STAGENET = "stagenet"
        const val TESTNET = "testnet"

        val defaultRpcPort: Int
            get() =
                when (AnonConfig.getNetworkType()) {
                    NetworkType.NetworkType_Mainnet -> 18081
                    NetworkType.NetworkType_Testnet -> 28081
                    NetworkType.NetworkType_Stagenet -> 38081
                }

        fun fromJson(jsonObject: JSONObject?): Node? =
            try {
                Node(jsonObject)
            } catch (_: IllegalArgumentException) {
                Timber.tag("Node.kt").w("Invalid node configuration")
                null
            } catch (_: JSONException) {
                Timber.tag("Node.kt").w("Unreadable node configuration")
                null
            }
    }
}
