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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportPolicyTest {
    @Test
    fun `maps hosts to destination networks`() {
        assertEquals(NodeNetwork.ONION, NodeNetwork.fromHost("abc.onion"))
        assertEquals(NodeNetwork.ONION, NodeNetwork.fromHost("ABC.ONION."))
        assertEquals(NodeNetwork.I2P, NodeNetwork.fromHost("route.i2p"))
        assertEquals(NodeNetwork.CLEARNET, NodeNetwork.fromHost("node.example.com"))
        assertEquals(NodeNetwork.CLEARNET, NodeNetwork.fromHost("127.0.0.1"))
        assertEquals(NodeNetwork.CLEARNET, NodeNetwork.fromHost("2001:db8::1"))
    }

    @Test
    fun `fixed transport network matrix`() {
        assertEquals(NodeNetwork.ONION, TransportConfig(mode = TransportMode.EMBEDDED_TOR).nodeNetwork)
        assertEquals(NodeNetwork.ONION, TransportConfig(mode = TransportMode.ORBOT).nodeNetwork)
        assertEquals(NodeNetwork.ONION, TransportConfig(mode = TransportMode.INVIZIBLE_TOR).nodeNetwork)
        assertEquals(NodeNetwork.CLEARNET, TransportConfig(mode = TransportMode.SYSTEM_VPN).nodeNetwork)
        assertEquals(NodeNetwork.I2P, TransportConfig(mode = TransportMode.I2PD).nodeNetwork)
        assertEquals(NodeNetwork.I2P, TransportConfig(mode = TransportMode.INVIZIBLE_I2P).nodeNetwork)
        assertEquals(NodeNetwork.I2P, TransportConfig(mode = TransportMode.I2P_JAVA).nodeNetwork)
    }

    @Test
    fun `custom proxy uses its explicit network`() {
        val config =
            TransportConfig(
                mode = TransportMode.CUSTOM_PROXY,
                proxyHost = "127.0.0.1",
                proxyPort = 9050,
                customNodeNetwork = NodeNetwork.ONION,
            )
        assertEquals(NodeNetwork.ONION, config.nodeNetwork)
        assertNull(config.validationError())
    }

    @Test
    fun `external Tor and I2P proxies must be local`() {
        val remote =
            TransportConfig(
                mode = TransportMode.ORBOT,
                proxyHost = "192.0.2.10",
                proxyPort = 9050,
            )
        assertEquals(TransportConfigError.EXTERNAL_PROXY_NOT_LOOPBACK, remote.validationError())

        val local = remote.copy(proxyHost = "127.0.0.1")
        assertNull(local.validationError())
    }

    @Test
    fun `invalid proxy endpoints are rejected`() {
        assertTrue(
            TransportConfig(
                mode = TransportMode.ORBOT,
                proxyHost = "",
                proxyPort = 0,
            ).validationError() == TransportConfigError.EXTERNAL_PROXY_REQUIRED,
        )
        assertTrue(
            TransportConfig(
                mode = TransportMode.ORBOT,
                proxyHost = "proxy.example.com",
                proxyPort = 9050,
            ).validationError() == TransportConfigError.INVALID_PROXY_HOST,
        )
        assertTrue(
            TransportConfig(
                mode = TransportMode.ORBOT,
                proxyHost = "127.0.0.1",
                proxyPort = 70000,
            ).validationError() == TransportConfigError.INVALID_PROXY_PORT,
        )
    }

    @Test
    fun `proxy URIs force SOCKS5 and bracket IPv6`() {
        assertEquals("socks5://127.0.0.1:9050", ProxyEndpoint("127.0.0.1", 9050).formatted)
        assertEquals("socks5://[::1]:9050", ProxyEndpoint("::1", 9050).formatted)
        assertFalse(ProxyEndpoint.isLoopback("192.0.2.1"))
        assertTrue(ProxyEndpoint.isLoopback("::1"))
        assertTrue(ProxyEndpoint.isNumericOrLoopback("2001:db8::1"))
        assertTrue(ProxyEndpoint.isNumericOrLoopback("localhost"))
        assertFalse(ProxyEndpoint.isNumericOrLoopback("proxy.example.com"))
    }

    @Test
    fun `provider release tracks mode proxy and network changes`() {
        val base =
            TransportConfig(
                mode = TransportMode.CUSTOM_PROXY,
                proxyHost = "127.0.0.1",
                proxyPort = 9050,
                customNodeNetwork = NodeNetwork.CLEARNET,
            )
        assertFalse(base.requiresProviderRelease(base.copy(autoSelectNode = false)))
        assertFalse(base.requiresProviderRelease(base.copy(preferTrustedNode = false)))
        assertTrue(base.requiresProviderRelease(base.copy(proxyHost = "127.0.0.2")))
        assertTrue(base.requiresProviderRelease(base.copy(proxyPort = 9051)))
        assertTrue(base.requiresProviderRelease(base.copy(customNodeNetwork = NodeNetwork.ONION)))
        assertTrue(base.requiresProviderRelease(base.copy(mode = TransportMode.EMBEDDED_TOR)))
    }
}
