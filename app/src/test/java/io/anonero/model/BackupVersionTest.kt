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

package io.anonero.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupVersionTest {
    @Test
    fun `parses integer and string versions`() {
        assertEquals(3, Json.decodeFromString(VersionAsIntSerializer, "3"))
        assertEquals(3, Json.decodeFromString(VersionAsIntSerializer, "\"3\""))
        assertEquals(3, Json.decodeFromString(VersionAsIntSerializer, "\"3.0\""))
    }

    @Test
    fun `rejects unknown version encodings`() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(VersionAsIntSerializer, "\"three\"")
        }
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(VersionAsIntSerializer, "{\"version\":3}")
        }
    }
}
