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
