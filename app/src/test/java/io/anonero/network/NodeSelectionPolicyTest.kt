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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeSelectionPolicyTest {
    private fun node(
        host: String,
        port: Int = 18081,
    ): Node =
        Node().apply {
            this.host = host
            this.rpcPort = port
            networkType = NetworkType.NetworkType_Mainnet
        }

    @Test
    fun `filters nodes by transport network`() {
        val nodes =
            listOf(
                node("abc.onion"),
                node("route.i2p"),
                node("node.example.com"),
            )
        val onion =
            NodeSelectionPolicy.compatibleNodes(
                nodes,
                TransportConfig(mode = TransportMode.EMBEDDED_TOR),
                NetworkType.NetworkType_Mainnet,
            )
        assertEquals(listOf("abc.onion"), onion.map { it.host })

        val i2p = NodeSelectionPolicy.compatibleNodes(nodes, TransportConfig(mode = TransportMode.I2PD), NetworkType.NetworkType_Mainnet)
        assertEquals(listOf("route.i2p"), i2p.map { it.host })

        val javaI2p =
            NodeSelectionPolicy.compatibleNodes(
                nodes,
                TransportConfig(mode = TransportMode.I2P_JAVA),
                NetworkType.NetworkType_Mainnet,
            )
        assertEquals(listOf("route.i2p"), javaI2p.map { it.host })

        val clearnet =
            NodeSelectionPolicy.compatibleNodes(
                nodes,
                TransportConfig(mode = TransportMode.SYSTEM_VPN),
                NetworkType.NetworkType_Mainnet,
            )
        assertEquals(listOf("node.example.com"), clearnet.map { it.host })
    }

    @Test
    fun `manual validation rejects a node from another network`() {
        val config = TransportConfig(mode = TransportMode.EMBEDDED_TOR)
        assertTrue(NodeSelectionPolicy.accepts(config, node("abc.onion")))
        assertFalse(NodeSelectionPolicy.accepts(config, node("node.example.com")))
        assertFalse(NodeSelectionPolicy.accepts(config, node("route.i2p")))
        assertFalse(NodeSelectionPolicy.accepts(config, null))
        assertFalse(
            NodeSelectionPolicy.accepts(
                TransportConfig(mode = TransportMode.SYSTEM_VPN),
                node(""),
            ),
        )
    }

    @Test
    fun `selects a compatible node and never crosses networks`() {
        val config = TransportConfig(mode = TransportMode.EMBEDDED_TOR, autoSelectNode = true)
        val selected =
            NodeSelectionPolicy.select(
                listOf(node("node.example.com"), node("abc.onion")),
                config,
                NetworkType.NetworkType_Mainnet,
            )
        assertEquals("abc.onion", selected?.host)

        assertNull(
            NodeSelectionPolicy.select(
                listOf(node("node.example.com")),
                config,
                NetworkType.NetworkType_Mainnet,
            ),
        )
    }
}
