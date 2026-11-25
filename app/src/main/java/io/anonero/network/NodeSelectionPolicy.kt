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

package io.anonero.network

import io.anonero.model.NetworkType
import io.anonero.model.node.Node

/**
 * Pure selection rules shared by the handler and the Network & Nodes screen.
 *
 * The transport fixes the destination network; a node whose host belongs to another
 * network is never a candidate. Latency/height are deliberately not fabricated: the
 * wallet connection reports real failures and the UI shows them. There is no separate
 * HTTP probe that could bypass the selected proxy.
 */
object NodeSelectionPolicy {
    fun compatibleNodes(
        nodes: List<Node>,
        config: TransportConfig,
        walletNetwork: NetworkType?,
    ): List<Node> =
        nodes
            .filter { accepts(config, it, walletNetwork) }
            .sortedWith(
                compareBy<Node> { config.preferTrustedNode && !it.trusted }
                    .thenBy { it.name.orEmpty().lowercase() }
                    .thenBy { it.host.orEmpty().lowercase() }
                    .thenBy { it.rpcPort },
            )

    fun select(
        nodes: List<Node>,
        config: TransportConfig,
        walletNetwork: NetworkType?,
        current: Node? = null,
    ): Node? {
        val candidates = compatibleNodes(nodes, config, walletNetwork)
        return candidates.firstOrNull { it == current } ?: candidates.firstOrNull()
    }

    fun accepts(
        config: TransportConfig,
        node: Node?,
        walletNetwork: NetworkType? = null,
    ): Boolean =
        node != null &&
            validHost(node.host) &&
            node.network == config.nodeNetwork &&
            (walletNetwork == null || node.networkType == walletNetwork) &&
            node.rpcPort in 1..65535

    /** A host is an authority component, never a URL, path or credential container. */
    private fun validHost(host: String?): Boolean {
        if (host.isNullOrEmpty() || host != host.trim() || host.length > 253) return false
        if (ProxyEndpoint.isNumericOrLoopback(host)) return true
        if (host.all { it in '0'..'9' || it == '.' }) return false
        return host.removeSuffix(".").split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isAsciiAlphanumeric() && label.last().isAsciiAlphanumeric() &&
                label.all { it.isAsciiAlphanumeric() || it == '-' }
        }
    }

    private fun Char.isAsciiAlphanumeric(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}
