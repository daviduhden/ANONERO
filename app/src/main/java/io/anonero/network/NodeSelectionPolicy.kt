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
            .filter { node ->
                node.rpcPort in 1..65535 &&
                    !node.host.isNullOrBlank() &&
                    (walletNetwork == null || node.networkType == walletNetwork) &&
                    node.network == config.nodeNetwork
            }.sortedWith(
                compareBy<Node> { !it.trusted }
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
        if (candidates.isEmpty()) return null
        if (current != null && candidates.any { it.address == current.address }) {
            return candidates.first { it.address == current.address }
        }
        return if (config.preferTrustedNode) {
            candidates.firstOrNull { it.trusted } ?: candidates.first()
        } else {
            candidates.first()
        }
    }

    fun accepts(
        config: TransportConfig,
        node: Node?,
    ): Boolean =
        node != null &&
            !node.host.isNullOrBlank() &&
            node.network == config.nodeNetwork &&
            node.rpcPort in 1..65535
}
