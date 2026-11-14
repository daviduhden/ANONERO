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

package io.anonero.util

import io.anonero.network.NodeNetwork
import io.anonero.network.TransportConfig
import io.anonero.network.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LwsConfigTest {
    @Test
    fun `valid onion and dns hosts pass`() {
        assertNull(LwsConfig.validate("lws.example.onion", 8443))
        assertNull(LwsConfig.validate("lws.example.com", 1, useTls = true))
        assertNull(LwsConfig.validate("127.0.0.1", 65535))
    }

    @Test
    fun `scheme credentials path and embedded port are rejected`() {
        assertNotNull(LwsConfig.validate("http://lws.example", 8443))
        assertNotNull(LwsConfig.validate("user:pass@lws.example", 8443))
        assertNotNull(LwsConfig.validate("lws.example/path", 8443))
        assertNotNull(LwsConfig.validate("lws.example:8443", 8443))
        assertNotNull(LwsConfig.validate("lws example", 8443))
        assertNotNull(LwsConfig.validate("", 8443))
    }

    @Test
    fun `ports outside the tcp range are rejected`() {
        assertNotNull(LwsConfig.validate("lws.example", 0))
        assertNotNull(LwsConfig.validate("lws.example", -1))
        assertNotNull(LwsConfig.validate("lws.example", 65536))
    }

    @Test
    fun `address includes the configured scheme`() {
        assertEquals("http://lws.example:8443", LwsConfig(true, "lws.example", 8443).address)
        assertEquals("https://lws.example:8443", LwsConfig(true, "lws.example", 8443, useTls = true).address)
    }

    @Test
    fun `clearnet LWS requires TLS unless local`() {
        assertNotNull(LwsConfig.validate("lws.example.com", 8443))
        assertNull(LwsConfig.validate("lws.example.com", 8443, useTls = true))
        assertNull(LwsConfig.validate("127.0.0.1", 8443))
    }

    @Test
    fun `LWS transport must match the destination network`() {
        val lws = LwsConfig(true, "lws.example.onion", 8443)
        assertEquals(NodeNetwork.ONION, lws.network)
        assertNull(LwsConfig.validateTransport(lws, TransportConfig(mode = TransportMode.ORBOT)))
        assertNotNull(LwsConfig.validateTransport(lws, TransportConfig(mode = TransportMode.SYSTEM_VPN)))

        val clearnet = LwsConfig(true, "lws.example.com", 8443, useTls = true)
        assertNull(LwsConfig.validateTransport(clearnet, TransportConfig(mode = TransportMode.SYSTEM_VPN)))
        assertNotNull(LwsConfig.validateTransport(clearnet, TransportConfig(mode = TransportMode.EMBEDDED_TOR)))
    }

    @Test
    fun `every transport only accepts its own LWS network`() {
        val onion = LwsConfig(true, "lws.example.onion", 8443)
        val i2p = LwsConfig(true, "lws.example.i2p", 8443)
        val clearnet = LwsConfig(true, "lws.example.com", 8443, useTls = true)

        listOf(
            TransportMode.EMBEDDED_TOR,
            TransportMode.ORBOT,
            TransportMode.INVIZIBLE_TOR,
        ).forEach { mode ->
            val transport = TransportConfig(mode = mode)
            assertNull(LwsConfig.validateTransport(onion, transport))
            assertNotNull(LwsConfig.validateTransport(i2p, transport))
            assertNotNull(LwsConfig.validateTransport(clearnet, transport))
        }

        listOf(
            TransportMode.I2PD,
            TransportMode.INVIZIBLE_I2P,
            TransportMode.I2P_JAVA,
        ).forEach { mode ->
            val transport = TransportConfig(mode = mode)
            assertNull(LwsConfig.validateTransport(i2p, transport))
            assertNotNull(LwsConfig.validateTransport(onion, transport))
            assertNotNull(LwsConfig.validateTransport(clearnet, transport))
        }

        val vpn = TransportConfig(mode = TransportMode.SYSTEM_VPN)
        assertNull(LwsConfig.validateTransport(clearnet, vpn))
        assertNotNull(LwsConfig.validateTransport(onion, vpn))
        assertNotNull(LwsConfig.validateTransport(i2p, vpn))
        assertNotNull(
            LwsConfig.validateTransport(
                LwsConfig(true, "lws.example.com", 8443, useTls = false),
                vpn,
            ),
        )

        val customOnion =
            TransportConfig(
                mode = TransportMode.CUSTOM_PROXY,
                customNodeNetwork = NodeNetwork.ONION,
            )
        assertNull(LwsConfig.validateTransport(onion, customOnion))
        assertNotNull(LwsConfig.validateTransport(clearnet, customOnion))
        assertNotNull(LwsConfig.validateTransport(i2p, customOnion))

        val customClearnet =
            TransportConfig(
                mode = TransportMode.CUSTOM_PROXY,
                customNodeNetwork = NodeNetwork.CLEARNET,
            )
        assertNull(LwsConfig.validateTransport(clearnet, customClearnet))
        assertNotNull(LwsConfig.validateTransport(onion, customClearnet))
        assertNotNull(LwsConfig.validateTransport(i2p, customClearnet))
    }

    @Test
    fun `onion and i2p endpoints do not require TLS but clearnet does`() {
        assertNull(LwsConfig.validate("lws.example.onion", 8443, useTls = false))
        assertNull(LwsConfig.validate("lws.example.i2p", 8443, useTls = false))
        assertNotNull(LwsConfig.validate("lws.example.com", 8443, useTls = false))
        assertNull(LwsConfig.validate("lws.example.com", 8443, useTls = true))
    }

    @Test
    fun `consent is required only for a new enabled server`() {
        val disabled = LwsConfig(false, "lws.example.onion", 8443)
        val enabled = disabled.copy(enabled = true)
        assertTrue(LwsConfig.requiresConsent(disabled, enabled))
        assertTrue(LwsConfig.requiresConsent(enabled, enabled.copy(host = "other.onion")))
        assertTrue(LwsConfig.requiresConsent(enabled, enabled.copy(port = 9443)))
        assertTrue(LwsConfig.requiresConsent(enabled, enabled.copy(useTls = true)))
        assertFalse(LwsConfig.requiresConsent(enabled, enabled))
        assertFalse(LwsConfig.requiresConsent(enabled, enabled.copy(enabled = false)))
    }
}
