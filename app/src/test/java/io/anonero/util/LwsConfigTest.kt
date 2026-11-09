package io.anonero.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LwsConfigTest {

    @Test
    fun `valid onion and dns hosts pass`() {
        assertNull(LwsConfig.validate("lws.example.onion", 8443))
        assertNull(LwsConfig.validate("lws.example.com", 1))
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
    fun `address joins host and port without a scheme`() {
        assertEquals("lws.example:8443", LwsConfig(true, "lws.example", 8443).address)
    }
}
